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

  @Attribute("email")
  public String getEmail() {
    return myEmail;
  }

  public void setEmail(String email) {
    myEmail = email;
  }

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
        return httpConfigurable.PROXY_LOGIN;
      }

      @Override
      public String getPassword() {
        return httpConfigurable.getPlainProxyPassword();
      }
    };
  }
}
