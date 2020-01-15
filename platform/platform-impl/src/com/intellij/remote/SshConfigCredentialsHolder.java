// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class SshConfigCredentialsHolder {

  private static final String SSH_CREDENTIALS_ID = "SSH_CREDENTIALS_ID";
  private static final String SSH_CONFIG_NAME = "SSH_CONFIG_NAME";

  private String myCredentialsId;
  private String mySshConfigName;

  public SshConfigCredentialsHolder() {
    this(null);
  }

  public SshConfigCredentialsHolder(String sshConfigName) {
    mySshConfigName = sshConfigName;
    updateCredentialsId();
  }

  public String getCredentialsId() {
    return myCredentialsId;
  }

  public String getSshConfigName() {
    return mySshConfigName;
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
    updateCredentialsId();
  }

  private void updateCredentialsId() {
    myCredentialsId = "sshConfig://" + (StringUtil.isEmpty(mySshConfigName) ? "<unknown>" : mySshConfigName);
  }

  public void copyFrom(SshConfigCredentialsHolder credentials) {
    myCredentialsId = credentials.myCredentialsId;
    mySshConfigName = credentials.mySshConfigName;
  }
}
