/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl;


import com.intellij.ide.IdeBundle;
import com.intellij.ide.reporter.ConnectionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.HttpConfigurable;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class UpdatesXmlLoader {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.updateSettings.impl.UpdatesXmlLoader");

  private final String updateUrl;

  public UpdatesXmlLoader(final String updatesUrl) {
    this.updateUrl = updatesUrl;
  }


  @Nullable
  public UpdatesInfo loadUpdatesInfo() throws ConnectionException{
    LOG.debug("load update xml (UPDATE_URL='" + updateUrl + "' )");

    if (StringUtil.isEmpty(updateUrl)) {
      LOG.debug("update url is empty: updates will not be checked");
      return null;
    }

    final Ref<Exception> error = new Ref<Exception>();
    FutureTask<UpdatesInfo> ft = new FutureTask<UpdatesInfo>(new Callable<UpdatesInfo>() {
      @Nullable
      @Override
      public UpdatesInfo call() throws Exception {
        try {
          prepareUrl(updateUrl);

          URL requestUrl = prepareRequestUrl(updateUrl);

          final InputStream inputStream = requestUrl.openStream();
          Reader reader = new InputStreamReader(inputStream);
          try {
            return new UpdatesInfo(JDOMUtil.loadDocument(inputStream).getRootElement());
          }
          catch (JDOMException e) {
            LOG.info(e); // Broken xml downloaded. Don't bother telling user.
          }
          finally {
            reader.close();
            inputStream.close();
          }
        }
        catch (Exception e) {
          error.set(e);
        }
        return null;
      }
    });
    ApplicationManager.getApplication().executeOnPooledThread(ft);
    try {
      UpdatesInfo result = ft.get(5, TimeUnit.SECONDS);
      if (!error.isNull()) {
        //noinspection ThrowableResultOfMethodCallIgnored
        throw new ConnectionException(error.get());
      }
      return result;
    }
    catch (TimeoutException e) {
      // ignore
    }
    catch (Exception e) {
      LOG.debug(e);
      throw new ConnectionException(e.getMessage(),e);
    }
    if (!ft.isDone()) {
      ft.cancel(true);
      throw new ConnectionException(IdeBundle.message("updates.timeout.error"));
    }
    return null;

  }


  protected static void prepareUrl(@NotNull String url) throws ConnectionException {
    try {
      HttpConfigurable.getInstance().prepareURL(url);
    }
    catch (Exception e) {
      throw new ConnectionException(e);
    }
  }

  protected URL prepareRequestUrl(@NotNull String url) throws ConnectionException {
    try {
      if (url.startsWith("file:")) {
        return new URL(url);
      }
      return new URL(url + "?" + UpdateChecker.prepareUpdateCheckArgs());
    }
    catch (Exception e) {
      throw new ConnectionException(e);
    }
  }
}
