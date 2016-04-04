/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtilRt;
import com.intellij.util.Url;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.net.ssl.CertificateManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

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
        .productNameAsUserAgent()
        .hostNameVerifier(CertificateManager.HOSTNAME_VERIFIER)
        .connect(new HttpRequests.RequestProcessor<Object>() {
          @Override
          public Object process(@NotNull HttpRequests.Request request) throws IOException {
            int size = request.getConnection().getContentLength();
            callback.setProgressText(VfsBundle.message("download.progress.downloading", presentableUrl), size == -1);
            request.saveToFile(file, new AbstractProgressIndicatorExBase() {
              @Override
              public void setFraction(double fraction) {
                callback.setProgressFraction(0);
              }
            });

            FileType fileType = RemoteFileUtil.getFileType(request.getConnection().getContentType());
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
