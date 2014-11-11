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


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.HttpRequests;
import com.intellij.util.ThrowableConvertor;
import com.intellij.util.Time;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.net.URLConnection;

class UpdatesXmlLoader {
  private static final Logger LOG = Logger.getInstance(UpdatesXmlLoader.class);

  @Nullable
  public static UpdatesInfo loadUpdatesInfo(@Nullable final String updateUrl) throws Exception {
    if (LOG.isDebugEnabled()) {
      LOG.debug("load update xml (UPDATE_URL='" + updateUrl + "' )");
    }

    if (StringUtil.isEmpty(updateUrl)) {
      LOG.debug("update url is empty: updates will not be checked");
      return null;
    }

    return HttpRequests.request(updateUrl.startsWith("file:") ? updateUrl : updateUrl + '?' + UpdateChecker.prepareUpdateCheckArgs())
      .connectTimeout(5 * Time.SECOND)
      .readTimeout(5 * Time.SECOND)
      .get(new ThrowableConvertor<URLConnection, UpdatesInfo, Exception>() {
        @Override
        public UpdatesInfo convert(URLConnection connection) throws Exception {
          InputStream inputStream = connection.getInputStream();
          try {
            return new UpdatesInfo(JDOMUtil.loadDocument(inputStream).getRootElement());
          }
          catch (JDOMException e) {
            // Broken xml downloaded. Don't bother telling user.
            LOG.info(e);
            return null;
          }
          finally {
            inputStream.close();
          }
        }
      });
  }
}
