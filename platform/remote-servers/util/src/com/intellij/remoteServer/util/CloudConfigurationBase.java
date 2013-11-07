package com.intellij.remoteServer.util;

import com.intellij.remoteServer.agent.util.CloudAgentConfigBase;
import com.intellij.remoteServer.configuration.ServerConfigurationBase;
import com.intellij.util.xmlb.annotations.Attribute;

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
}
