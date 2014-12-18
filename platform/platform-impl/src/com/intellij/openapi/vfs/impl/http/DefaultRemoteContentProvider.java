/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.impl.http;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtilRt;
import com.intellij.util.Url;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.net.ssl.CertificateManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.Responses;

import java.io.*;
import java.net.HttpURLConnection;

public class DefaultRemoteContentProvider extends RemoteContentProvider {
  private static final Logger LOG = Logger.getInstance(DefaultRemoteContentProvider.class);

  @Override
  public boolean canProvideContent(@NotNull Url url) {
    return true;
  }

  @Override
  public void saveContent(@NotNull final Url url, @NotNull final File file, @NotNull final DownloadingCallback callback) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        downloadContent(url, file, callback);
      }
    });
  }

  private static void downloadContent(@NotNull final Url url, final File file, final DownloadingCallback callback) {
    LOG.debug("Downloading started: " + url);
    final String presentableUrl = StringUtil.trimMiddle(url.trimParameters().toDecodedForm(), 40);
    callback.setProgressText(VfsBundle.message("download.progress.connecting", presentableUrl), true);
    try {
      HttpRequests.request(url.toExternalForm())
        .connectTimeout(60 * 1000)
        .readTimeout(60 * 1000)
        .userAgent(Responses.getServerHeaderValue())
        .hostNameVerifier(CertificateManager.HOSTNAME_VERIFIER)
        .connect(new HttpRequests.RequestProcessor<Object>() {
          @Override
          public Object process(@NotNull HttpRequests.Request request) throws IOException {
            HttpURLConnection connection = (HttpURLConnection)request.getConnection();
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
              throw new IOException(IdeBundle.message("error.connection.failed.with.http.code.N", responseCode));
            }

            int size = connection.getContentLength();
            OutputStream output = new BufferedOutputStream(new FileOutputStream(file));
            try {
              callback.setProgressText(VfsBundle.message("download.progress.downloading", presentableUrl), size == -1);
              if (size != -1) {
                callback.setProgressFraction(0);
              }

              int count;
              byte[] buf = new byte[4096];
              int total = 0;
              while ((count = request.getInputStream().read(buf)) > 0) {
                if (callback.isCancelled()) {
                  return null;
                }
                total += count;
                if (size > 0) {
                  callback.setProgressFraction((double)total / size);
                }
                output.write(buf, 0, count);
              }
            }
            finally {
              output.close();
            }

            FileType fileType = RemoteFileUtil.getFileType(connection.getContentType());
            if (fileType == FileTypes.PLAIN_TEXT) {
              FileType fileTypeByFileName = FileTypeRegistry.getInstance().getFileTypeByFileName(PathUtilRt.getFileName(url.getPath()));
              if (fileTypeByFileName != FileTypes.UNKNOWN) {
                fileType = fileTypeByFileName;
              }
            }

            LOG.debug("Downloading finished, " + size + " bytes downloaded");
            callback.finished(fileType);
            return null;
          }
        });
    }
    catch (IOException e) {
      LOG.info(e);
      callback.errorOccurred(VfsBundle.message("cannot.load.remote.file", url, e.getMessage()), false);
    }
  }

  @Override
  public boolean isUpToDate(@NotNull final Url url, @NotNull final VirtualFile local) {
    return false;
  }
}
