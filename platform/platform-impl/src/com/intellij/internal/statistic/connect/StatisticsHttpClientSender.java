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
package com.intellij.internal.statistic.connect;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.HttpConfigurable;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.jetbrains.annotations.NotNull;

public class StatisticsHttpClientSender implements StatisticsDataSender {
  @Override
  public void send(@NotNull String url, @NotNull String content) throws StatServiceException {
    try {
      HttpConfigurable.getInstance().prepareURL(url);

      Response response = Request.Post(url).bodyForm(Form.form().
        add("content", content).
        add("uuid", UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())).
        add("ide", ApplicationNamesInfo.getInstance().getProductName()).build()).execute();
      if (response.returnResponse().getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
        throw new StatServiceException("Error during data sending... Code: " + response.returnResponse().getStatusLine().getStatusCode());
      }

      final Header errors = response.returnResponse().getFirstHeader("errors");
      if (errors != null) {
        String value = errors.getValue();
        throw new StatServiceException("Error during updating statistics " + (!StringUtil.isEmptyOrSpaces(value) ? " : " + value : ""));
      }
    }
    catch (StatServiceException e) {
      throw e;
    }
    catch (Exception e) {
      throw new StatServiceException("Error during data sending...", e);
    }
  }
}
