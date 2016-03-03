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
package com.intellij.remote.ext;

import com.intellij.remote.RemoteCredentials;
import com.intellij.remote.WebDeploymentCredentialsHolder;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WebDeploymentCredentialsHandler extends RemoteCredentialsHandlerBase<WebDeploymentCredentialsHolder> {

  public static final String SFTP_DEPLOYMENT_PREFIX = "sftp://";

  public WebDeploymentCredentialsHandler(WebDeploymentCredentialsHolder credentials) {
    super(credentials);
  }

  @Override
  public String getId() {
    return constructSftpCredentialsFullPath();
  }

  @Override
  public void save(@NotNull Element rootElement) {
    getCredentials().save(rootElement);
  }

  @Override
  public String getPresentableDetails(String interpreterPath) {
    return "(" + constructSftpCredentialsFullPath() + interpreterPath + ")";
  }

  @Override
  public void load(@Nullable Element rootElement) {
    WebDeploymentCredentialsHolder credentials = getCredentials();
    if (rootElement != null) {
      credentials.load(rootElement);
    }
    else {
      credentials.setWebServerConfigId("");
      credentials.setWebServerConfigName("Invalid");
    }
  }

  @NotNull
  private String constructSftpCredentialsFullPath() {
    RemoteCredentials cred = getCredentials().getSshCredentials();
    return SFTP_DEPLOYMENT_PREFIX + cred.getUserName() + "@" + cred.getHost() + ":" + cred.getLiteralPort();
  }
}
