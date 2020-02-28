// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SshConfigCredentialsHolder {

  private static final String SSH_CREDENTIALS_ID = "SSH_CREDENTIALS_ID";
  private static final String SSH_CONFIG_NAME = "SSH_CONFIG_NAME";
  public static final String SSH_CONFIG_PREFIX = "sshConfig://";

  private String myCredentialsId;
  private String mySshConfigName;
  private String mySshConfigId;

  public SshConfigCredentialsHolder() {
    this(null, null);
  }

  public SshConfigCredentialsHolder(@Nullable String sshConfigName, @Nullable String sshConfigId) {
    mySshConfigName = sshConfigName;
    mySshConfigId = sshConfigId;
    updateCredentialsId();
  }

  public String getCredentialsId() {
    return myCredentialsId;
  }

  public String getSshConfigName() {
    return mySshConfigName;
  }

  public void setSshConfigName(String sshConfigName) {
    mySshConfigName = sshConfigName;
  }

  public String getSshConfigId() {
    return mySshConfigId;
  }

  public void setSshConfigId(String sshConfigId) {
    mySshConfigId = sshConfigId;
  }

  public void save(@NotNull Element element) {
    element.setAttribute(SSH_CONFIG_NAME, StringUtil.notNullize(mySshConfigName));
    element.setAttribute(SSH_CREDENTIALS_ID, StringUtil.notNullize(myCredentialsId));
  }

  public void load(@NotNull Element element) {
    mySshConfigName = element.getAttributeValue(SSH_CONFIG_NAME);
    myCredentialsId = element.getAttributeValue(SSH_CREDENTIALS_ID);
  }

  public void cleanConfigData() {
    mySshConfigName = null;
    mySshConfigId = null;
    updateCredentialsId();
  }

  private void updateCredentialsId() {
    myCredentialsId = SSH_CONFIG_PREFIX + (StringUtil.isEmpty(mySshConfigName) ? "<unknown>" : mySshConfigName);
  }

  public void copyFrom(SshConfigCredentialsHolder credentials) {
    myCredentialsId = credentials.myCredentialsId;
    mySshConfigName = credentials.mySshConfigName;
    mySshConfigId = credentials.mySshConfigId;
  }
}
