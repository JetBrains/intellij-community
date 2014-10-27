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
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.concurrent.*;

class UpdatesXmlLoader {
  private static final Logger LOG = Logger.getInstance(UpdatesXmlLoader.class);

  @Nullable
  public static UpdatesInfo loadUpdatesInfo(@Nullable final String updateUrl) throws ConnectionException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("load update xml (UPDATE_URL='" + updateUrl + "' )");
    }

    if (StringUtil.isEmpty(updateUrl)) {
      LOG.debug("update url is empty: updates will not be checked");
      return null;
    }

    final Ref<Exception> error = new Ref<Exception>();
    Future<UpdatesInfo> future = ApplicationManager.getApplication().executeOnPooledThread(new Callable<UpdatesInfo>() {
      @Nullable
      @Override
      public UpdatesInfo call() throws Exception {
        try {
          String url = updateUrl.startsWith("file:") ? updateUrl : updateUrl + '?' + UpdateChecker.prepareUpdateCheckArgs();
          InputStream inputStream = HttpConfigurable.getInstance().openConnection(url).getInputStream();
          try {
            return new UpdatesInfo(JDOMUtil.loadDocument(inputStream).getRootElement());
          }
          catch (JDOMException e) {
            LOG.info(e); // Broken xml downloaded. Don't bother telling user.
          }
          finally {
            inputStream.close();
          }
        }
        catch (Exception e) {
          LOG.debug(e);
          error.set(e);
        }
        return null;
      }
    });

    UpdatesInfo result = null;
    try {
      result = future.get(5, TimeUnit.SECONDS);
    }
    catch (TimeoutException ignored) {
    }
    catch (InterruptedException e) {
      LOG.debug(e);
      error.set(e);
    }
    catch (ExecutionException e) {
      LOG.debug(e);
      error.set(e);
    }

    if (!future.isDone()) {
      future.cancel(true);
      if (error.isNull()) {
        throw new ConnectionException(IdeBundle.message("updates.timeout.error"));
      }
    }
    if (!error.isNull()) {
      throw new ConnectionException(error.get());
    }
    return result;
  }
}
