// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class WebDeploymentCredentialsHolder {
  @NonNls private static final String SFTP_DEPLOYMENT_PREFIX = "sftp://";

  @NonNls public static final String WEB_SERVER_CREDENTIALS_ID = "WEB_SERVER_CREDENTIALS_ID";
  @NonNls public static final String WEB_SERVER_CONFIG_ID = "WEB_SERVER_CONFIG_ID";
  @NonNls public static final String WEB_SERVER_CONFIG_NAME = "WEB_SERVER_CONFIG_NAME";


  private @NotNull String myCredentialsId = "";
  private @NotNull String myWebServerConfigId = "";
  private String myWebServerConfigName;


  public WebDeploymentCredentialsHolder() {
  }

  public WebDeploymentCredentialsHolder(@NotNull String webServerConfigId, String name, @NotNull RemoteCredentials remoteCredentials) {
    myWebServerConfigId = webServerConfigId;
    myWebServerConfigName = name;
    myCredentialsId = constructSftpCredentialsFullPath(remoteCredentials);
  }

  public @NotNull String getCredentialsId() {
    return myCredentialsId;
  }

  public @NotNull String getWebServerConfigId() {
    return myWebServerConfigId;
  }

  public void setWebServerConfigId(@NotNull String webServerConfigId) {
    myWebServerConfigId = webServerConfigId;
  }

  public String getWebServerConfigName() {
    return myWebServerConfigName;
  }

  public void setWebServerConfigName(@NotNull String name) {
    myWebServerConfigName = name;
  }

  public void load(Element element) {
    setWebServerConfigId(StringUtil.notNullize(element.getAttributeValue(WEB_SERVER_CONFIG_ID)));
    setWebServerConfigName(StringUtil.notNullize(element.getAttributeValue(WEB_SERVER_CONFIG_NAME)));
    myCredentialsId = StringUtil.notNullize(element.getAttributeValue(WEB_SERVER_CREDENTIALS_ID));
    if (StringUtil.isEmpty(myCredentialsId)) {
      // loading old settings -> convert previously saved credentials to id
      final RemoteCredentialsHolder credentials = new RemoteCredentialsHolder();
      credentials.load(element);
      myCredentialsId = constructSftpCredentialsFullPath(credentials);
    }
  }

  public void save(Element element) {
    element.setAttribute(WEB_SERVER_CONFIG_ID, getWebServerConfigId());
    element.setAttribute(WEB_SERVER_CONFIG_NAME, getWebServerConfigName());
    element.setAttribute(WEB_SERVER_CREDENTIALS_ID, StringUtil.notNullize(getCredentialsId()));
  }

  @NotNull
  public WebDeploymentCredentialsHolder copyFrom(@NotNull WebDeploymentCredentialsHolder holder) {
    setWebServerConfigId(holder.getWebServerConfigId());
    setWebServerConfigName(holder.getWebServerConfigName());
    myCredentialsId = holder.getCredentialsId();
    return this;
  }

  @NotNull
  private static String constructSftpCredentialsFullPath(@NotNull RemoteCredentials cred) {
    return SFTP_DEPLOYMENT_PREFIX + cred.getUserName() + "@" + cred.getHost() + ":" + cred.getLiteralPort();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    WebDeploymentCredentialsHolder holder = (WebDeploymentCredentialsHolder)o;

    if (!myWebServerConfigId.equals(holder.myWebServerConfigId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myWebServerConfigId.hashCode();
  }
}

