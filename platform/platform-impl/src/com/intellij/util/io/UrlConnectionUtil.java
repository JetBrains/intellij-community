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
package com.intellij.util.io;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author nik
 */
public class UrlConnectionUtil {
  private UrlConnectionUtil() {
  }

  @Nullable
  public static InputStream getConnectionInputStream(@NotNull URLConnection connection, @Nullable ProgressIndicator progressIndicator) {
    try {
      return getConnectionInputStreamWithException(connection, progressIndicator);
    }
    catch (ProcessCanceledException e) {
      return null;
    }
    catch (IOException e) {
      return null;
    }
  }

  @NotNull
  public static InputStream getConnectionInputStreamWithException(@NotNull URLConnection connection, @Nullable ProgressIndicator progressIndicator) throws IOException {
    if (ApplicationManager.getApplication() == null) {
      return connection.getInputStream();
    }

    InputStreamGetter getter = new InputStreamGetter(connection);
    final Future<?> getterFuture = ApplicationManager.getApplication().executeOnPooledThread(getter);
    while (true) {
      if (progressIndicator != null) {
        progressIndicator.checkCanceled();
      }

      try {
        try {
          getterFuture.get(50, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException ignored) {
        }

        if (progressIndicator != null) {
          progressIndicator.setIndeterminate(true);
          progressIndicator.setText(progressIndicator.getText());
        }

        if (getterFuture.isDone()) {
          break;
        }
      }
      catch (Exception e) {
        throw new ProcessCanceledException(e);
      }
    }
    //noinspection ThrowableResultOfMethodCallIgnored
    if (getter.getException() != null) {
      throw getter.getException();
    }

    return getter.getInputStream();
  }

  public static class InputStreamGetter implements Runnable {
    private InputStream myInputStream;
    private final URLConnection myUrlConnection;
    private IOException myException;

    public InputStreamGetter(URLConnection urlConnection) {
      myUrlConnection = urlConnection;
    }

    public IOException getException() {
      return myException;
    }

    public InputStream getInputStream() {
      return myInputStream;
    }

    @Override
    public void run() {
      try {
        myInputStream = myUrlConnection.getInputStream();
      }
      catch (IOException e) {
        myException = e;
        myInputStream = null;
      }
      catch (Exception e) {
        myException = new IOException();
        myException.initCause(e);
        myInputStream = null;
      }
    }
  }
}
