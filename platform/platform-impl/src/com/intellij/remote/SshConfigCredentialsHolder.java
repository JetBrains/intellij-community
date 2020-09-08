// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SshConfigCredentialsHolder {

  private static final @NonNls String SSH_CREDENTIALS_ID = "SSH_CREDENTIALS_ID";
  private static final @NonNls String SSH_CONFIG_NAME = "SSH_CONFIG_NAME";
  private static final @NonNls String SSH_CONFIG_ID = "SSH_CONFIG_ID";
  public static final @NonNls String SSH_CONFIG_PREFIX = "sshConfig://";

  private @NotNull String myCredentialsId;
  private @Nullable PresentableId mySshId;

  public SshConfigCredentialsHolder() {
    this(null);
  }

  public SshConfigCredentialsHolder(@Nullable PresentableId presentableId) {
    mySshId = presentableId;
    myCredentialsId = constructCredentialsId();
  }

  public @NotNull String getCredentialsId() {
    return myCredentialsId;
  }

  public @Nullable PresentableId getSshId() {
    return mySshId;
  }

  public void setSshId(@Nullable PresentableId sshId) {
    mySshId = sshId;
  }

  public void save(@NotNull Element element) {
    if (mySshId != null) {
      if (mySshId.getId() != null) {
        element.setAttribute(SSH_CONFIG_ID, mySshId.getId());
      }
      if (mySshId.getName() != null) {
        element.setAttribute(SSH_CONFIG_NAME, mySshId.getName());
      }
    }
    element.setAttribute(SSH_CREDENTIALS_ID, StringUtil.notNullize(myCredentialsId));
  }

  public void load(@NotNull Element element) {
    mySshId = PresentableId.createId(element.getAttributeValue(SSH_CONFIG_ID), element.getAttributeValue(SSH_CONFIG_NAME));
    String credentialsId = element.getAttributeValue(SSH_CREDENTIALS_ID);
    myCredentialsId = credentialsId == null ? constructCredentialsId() : credentialsId;
  }

  public void cleanConfigData() {
    mySshId = null;
    myCredentialsId = constructCredentialsId();
  }

  @NonNls
  @NotNull
  private String constructCredentialsId() {
    return SSH_CONFIG_PREFIX + ((mySshId == null || mySshId.getName() == null) ? "<unknown>" : mySshId.getName());
  }

  public void copyFrom(SshConfigCredentialsHolder credentials) {
    myCredentialsId = credentials.myCredentialsId;
    mySshId = credentials.mySshId == null ? null : credentials.mySshId.clone();
  }
}
