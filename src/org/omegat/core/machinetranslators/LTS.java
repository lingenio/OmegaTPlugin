package org.omegat.core.machinetranslators;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import java.net.ConnectException;
import java.net.UnknownHostException;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import org.omegat.util.Language;
import org.omegat.util.Preferences;

import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Support of Lingenio GmbH Translation Server machine translation.
 *
 * @author Michael Knaf (m.knaf@lingenio.de)
 */

public class LTS extends BaseTranslate {

    private URL url = new URL("http://cluster1.wwwtranslate.eu:8129/lts/json?func=translate");

	public LTS() throws Exception {
	}
	
	@Override
	protected String getPreferenceName() {
		return "allow_lingenio_translate";
	}

	public String getName() {
		return "Lingenio Translation Server";
	}

	@Override
	protected String translate(Language sLang, Language tLang, String text) throws Exception {
       
        HttpURLConnection conn = (HttpURLConnection) this.url.openConnection();
        
        // get apikey
        String apikey = System.getProperty("lingenio.api.key");
        if (apikey == null) {
            return "LTS APIKey not found!";
        }
        // write apikey to headers
        conn.setRequestProperty("X-LTS-Apikey", apikey);

        // prepare the tr_obj
        JSONObject tr_obj = new JSONObject();
        tr_obj.put("src", sLang.toString().toLowerCase());
        tr_obj.put("trg", tLang.toString().toLowerCase());
        tr_obj.put("dom", "");
        
        JSONArray sgms = new JSONArray().put(
            new JSONObject().put(
                "units", new JSONArray().put(
                    new JSONObject().put(
                        "text", text)
                    )
                )
            );
        tr_obj.put("sgms", sgms);

        // write the tr_obj as a string
        String body = new String(tr_obj.toString().getBytes());

        // adjust some request attributes
        conn.setRequestMethod("POST");
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("charset", "utf-8");
        conn.setRequestProperty("Content-Length", "" + Integer.toString(body.getBytes().length));

       
        try {
            // write body. this actually opens the connection and does the request
            OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), "UTF8");
            writer.write(body);
            writer.flush();
            writer.close();
        } catch (UnknownHostException e) {
            return "No route to host. Is your internet connection up and running?";
        } catch (ConnectException e) {
            return "It seems the server is down.";
        }

        // check the return codes
        int responseCode = conn.getResponseCode();
        
        // close connection
        switch (responseCode) {
            case 402: conn.disconnect();
                      return "The Server says 'Payment required'. Either your LTS APIKey is invalid or your character Quota has been used up.";
            case 400: conn.disconnect();
                      return "The Server wasn't able to read the request. It returned a HTTP Error 400.";
            case 500: conn.disconnect();
                      return "The Server encountered an internal Érror (HTTP Error 500)";
            case 200: break;
            default: return "An unknown error occured.";
        }


        // get the result
        String response = "";
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream()));
        for (String line; (line = reader.readLine()) != null; ) {
            response = response + line;
        }

        // close buffers
        reader.close();

        // extract the relevant stuff from the JSON
        String translation = "";
        JSONArray units = new JSONObject(response).getJSONArray("sgms").getJSONObject(0).getJSONArray("units");

        // somehow the 'size' and other stuff isn't inherited correctly,
        // so let's just rock over all the elements until we fail... lol, hackz0rz.
        try {
            for (int i = 0; true; i++) {
                translation += units.getJSONObject(i).getString("text");
            }
        } catch (Exception e) {
        }

        return translation;
	}
}
