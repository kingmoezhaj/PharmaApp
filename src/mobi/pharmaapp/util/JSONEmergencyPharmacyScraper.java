package mobi.pharmaapp.util;

import android.app.Activity;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import mobi.pharmaapp.models.DataModel;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StreamCorruptedException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author see /AUTHORS
 */
public class JSONEmergencyPharmacyScraper {

    private static boolean needsUpdate() {
        long lastUpdate = DataModel.getInstance().getEmergencyPharmaciesContainer().getSharedPreferences("PREFERENCE", Activity.MODE_PRIVATE).getLong("date_em_pharm_data", 0);
        return System.currentTimeMillis() - lastUpdate > 30 * 60 * 1000;
    }

    private static JSONArray readCache() {
        JSONArray arr = null;
        try {
            BufferedReader br = new BufferedReader(new FileReader(new File(new File(DataModel.getInstance().getEmergencyPharmaciesContainer().getCacheDir(), "") + "JSONcache_em_pharm.srl")));
            String line, content = "";
            while ((line = br.readLine()) != null) {
                content += line;
            }
            arr = new JSONArray(content);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(JSONPharmacyScraper.class.getName()).log(Level.SEVERE, null, ex);
        } catch (StreamCorruptedException ex) {
            Logger.getLogger(JSONPharmacyScraper.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(JSONPharmacyScraper.class.getName()).log(Level.SEVERE, null, ex);
        } catch (JSONException ex) {
            Logger.getLogger(JSONPharmacyScraper.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            return arr;
        }
    }

    public static int loadData(boolean force) {
        JSONArray arr;
        if (force || (needsUpdate() && isNetworkAvailable())) {
            arr = downloadData();
        } else {
            arr = readCache();
        }
        long lastUpdate = DataModel.getInstance().getEmergencyPharmaciesContainer().getSharedPreferences("PREFERENCE", Activity.MODE_PRIVATE).getLong("date_em_pharm_data", 0);
        DataModel.getInstance().setLastEmPharmsUpdate(new Date(lastUpdate));
        return fetchData(arr);
    }

    private static boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) DataModel.getInstance().getEmergencyPharmaciesContainer().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null;
    }

    protected static InputStream getStream(String full_url) throws IOException {
        URL url = new URL(full_url);
        URLConnection urlConnection = url.openConnection();
        urlConnection.setConnectTimeout(1000);
        return urlConnection.getInputStream();
    }

    protected static JSONArray downloadData() {
        String result;
        try {
            InputStream inp = getStream(LocalConstants.EMERGENCY_JSON);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inp, LocalConstants.ENCODING), 8);
            StringBuilder builder = new StringBuilder();
            builder.append(reader.readLine()).append("\n");

            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            inp.close();
            result = builder.toString();
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(JSONPharmacyScraper.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        } catch (IOException e) {
            Logger.getLogger(JSONPharmacyScraper.class.getName()).log(Level.SEVERE, null, e);
            return null;
        }
        JSONArray arr = null;
        try {
            if (!result.isEmpty() && !result.equals("null\n")) {
                JSONObject obj = new JSONObject(result);
                arr = obj.getJSONArray("Apotheken");
            }
        } catch (JSONException ex) {
            Logger.getLogger(JSONPharmacyScraper.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                BufferedWriter out = new BufferedWriter(new FileWriter(new File(DataModel.getInstance().getEmergencyPharmaciesContainer().getCacheDir(), "") + "JSONcache_em_pharm.srl"));
                out.write(arr.toString());
                out.close();
                DataModel.getInstance().getEmergencyPharmaciesContainer().getSharedPreferences("PREFERENCE", Activity.MODE_PRIVATE)
                        .edit()
                        .putLong("date_em_pharm_data", System.currentTimeMillis())
                        .commit();
            } catch (FileNotFoundException ex) {
                Logger.getLogger(JSONPharmacyScraper.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(JSONPharmacyScraper.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                return arr;
            }
        }
    }

    protected static int fetchData(JSONArray arr) {
        if (arr == null) {
            return 1;
        }
        DataModel.getInstance().resetEmergencyPharmacists();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                String address = obj.getString("street") + " " + obj.getString("nr");
                Pharmacy a = new Pharmacy((float) 0, (float) 0, obj.getString("name"), address, 0, "0", "0", Integer.parseInt(obj.getString("zip")), obj.getString("city"), obj.getString("tel"));
                findCoordinates(a);
                DataModel.getInstance().addEmergencyPharmacy(a);
            } catch (JSONException ex) {
                Logger.getLogger(JSONPharmacyScraper.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NullPointerException e) {
                Logger.getLogger(JSONPharmacyScraper.class.getName()).log(Level.SEVERE, null, e);
            }
        }
        return 0;
    }

    private static void findCoordinates(Pharmacy a) {
        try {
            Geocoder geocoder = new Geocoder(DataModel.getInstance().getEmergencyPharmaciesContainer().getApplicationContext(), Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocationName(a.getAddress() + " " + a.getTown(), 1);
            if (addresses.size() > 0) {
                a.setLocation(new Location((float) addresses.get(0).getLatitude(),
                        (float) addresses.get(0).getLongitude()));
            }
        } catch (IOException ex) {
        }
    }
}