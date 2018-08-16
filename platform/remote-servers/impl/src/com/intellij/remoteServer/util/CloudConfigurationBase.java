// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.remoteServer.agent.util.CloudAgentConfigBase;
import com.intellij.remoteServer.agent.util.CloudProxySettings;
import com.intellij.remoteServer.configuration.ServerConfigurationBase;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;

/**
 * @author michael.golubev
 */

public class CloudConfigurationBase<Self extends CloudConfigurationBase<Self>>
  extends ServerConfigurationBase<Self> implements CloudAgentConfigBase {

  private String myEmail;

  private String myPassword;

  @Override
  @Attribute("email")
  public String getEmail() {
    return myEmail;
  }

  public void setEmail(String email) {
    myEmail = email;
  }

  @Override
  @Attribute("password")
  public String getPassword() {
    return myPassword;
  }

  public void setPassword(String password) {
    myPassword = password;
  }

  @Transient
  @Override
  public CloudProxySettings getProxySettings() {
    final HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
    return new CloudProxySettings() {

      @Override
      public boolean useHttpProxy() {
        return httpConfigurable.USE_HTTP_PROXY;
      }

      @Override
      public String getHost() {
        return httpConfigurable.PROXY_HOST;
      }

      @Override
      public int getPort() {
        return httpConfigurable.PROXY_PORT;
      }

      @Override
      public boolean useAuthentication() {
        return httpConfigurable.PROXY_AUTHENTICATION;
      }

      @Override
      public String getLogin() {
        return httpConfigurable.getProxyLogin();
      }

      @Override
      public String getPassword() {
        return httpConfigurable.getPlainProxyPassword();
      }
    };
  }
}
