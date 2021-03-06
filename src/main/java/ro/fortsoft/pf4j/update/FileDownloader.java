/*
 * Copyright 2014 Decebal Suiu
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance with
 * the License. You may obtain a copy of the License in the LICENSE file, or at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package ro.fortsoft.pf4j.update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * Downloads a file from a URL.
 *
 * @author Decebal Suiu
 */
class FileDownloader {

    private static final Logger log = LoggerFactory.getLogger(FileDownloader.class);

    private static final String pluginsDir = System.getProperty("pf4j.pluginsDir", "plugins");

    public File downloadFile(String fileUrl) throws IOException {
        File plugins = new File(pluginsDir);
        plugins.mkdirs();

        // create a temporary file
        File tmpFile = new File(pluginsDir, DigestUtils.getSHA1(fileUrl) + ".tmp");
        if (tmpFile.exists()) {
            tmpFile.delete();
        }

        log.debug("Download '{}' to '{}'", fileUrl, tmpFile);

        // create the url
        URL url = new URL(fileUrl);

        // set up the URL connection
        URLConnection connection = url.openConnection();

        // connect to the remote site (may takes some time)
        connection.connect();

        // check for http authorization
        HttpURLConnection httpConnection = (HttpURLConnection) connection;
        if (httpConnection.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
            throw new ConnectException("HTTP Authorization failure");
        }

        // try to get the server-specified last-modified date of this artifact
        long lastModified = httpConnection.getHeaderFieldDate("Last-Modified", System.currentTimeMillis());

        // try to get the input stream (three times)
        InputStream is = null;
        for (int i = 0; i < 3; i++) {
            try {
                is = connection.getInputStream();
                break;
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        if (is == null) {
            throw new ConnectException("Can't get '" + url + " to '" + tmpFile + "'");
        }

        // reade from remote resource and write to the local file
        FileOutputStream fos = new FileOutputStream(tmpFile);
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) >= 0) {
            fos.write(buffer, 0, length);
        }
        if (fos != null) {
            fos.close();
        }
        is.close();

        // rename tmp file to resource file
        String path = url.getPath();
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        File file = new File(plugins, fileName);
        if (file.exists()) {
            log.debug("Delete old '{}' resource file", file);
            file.delete();
        }
        log.debug("Rename '{}' to {}", tmpFile, file);
        tmpFile.renameTo(file);


        log.debug("Set last modified of '{}' to '{}'", file, lastModified);
        file.setLastModified(lastModified);

        return file;
    }

}
