/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtilRt;
import com.intellij.util.Url;
import com.intellij.util.io.UrlConnectionUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author nik
 */
public class DefaultRemoteContentProvider extends RemoteContentProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.http.DefaultRemoteContentProvider");
  private static final int CONNECT_TIMEOUT = 60 * 1000;
  private static final int READ_TIMEOUT = 60 * 1000;

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
    InputStream input = null;
    OutputStream output = null;
    try {
      String presentableUrl = StringUtil.trimMiddle(url.trimParameters().toDecodedForm(), 40);
      callback.setProgressText(VfsBundle.message("download.progress.connecting", presentableUrl), true);
      HttpURLConnection connection = (HttpURLConnection)new URL(url.toExternalForm()).openConnection();
      connection.setConnectTimeout(CONNECT_TIMEOUT);
      connection.setReadTimeout(READ_TIMEOUT);
      input = UrlConnectionUtil.getConnectionInputStreamWithException(connection, new EmptyProgressIndicator());

      final int responseCode = connection.getResponseCode();
      if (responseCode != HttpURLConnection.HTTP_OK) {
        throw new IOException(IdeBundle.message("error.connection.failed.with.http.code.N", responseCode));
      }

      final int size = connection.getContentLength();
      //noinspection IOResourceOpenedButNotSafelyClosed
      output = new BufferedOutputStream(new FileOutputStream(file));
      callback.setProgressText(VfsBundle.message("download.progress.downloading", presentableUrl), size == -1);
      if (size != -1) {
        callback.setProgressFraction(0);
      }

      FileType fileType = RemoteFileUtil.getFileType(connection.getContentType());
      if (fileType == FileTypes.PLAIN_TEXT) {
        FileType fileTypeByFileName = FileTypeRegistry.getInstance().getFileTypeByFileName(PathUtilRt.getFileName(url.getPath()));
        if (fileTypeByFileName != FileTypes.UNKNOWN) {
          fileType = fileTypeByFileName;
        }
      }

      int len;
      final byte[] buf = new byte[1024];
      int count = 0;
      while ((len = input.read(buf)) > 0) {
        if (callback.isCancelled()) {
          return;
        }
        count += len;
        if (size > 0) {
          callback.setProgressFraction((double)count / size);
        }
        output.write(buf, 0, len);
      }
      output.close();
      output = null;
      LOG.debug("Downloading finished, " + size + " bytes downloaded");
      callback.finished(fileType);
    }
    catch (IOException e) {
      LOG.info(e);
      callback.errorOccurred(VfsBundle.message("cannot.load.remote.file", url, e.getMessage()), false);
    }
    finally {
      if (input != null) {
        try {
          input.close();
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
      if (output != null) {
        try {
          output.close();
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    }
  }

  @Override
  public boolean isUpToDate(@NotNull final Url url, @NotNull final VirtualFile local) {
    return false;
  }
}
