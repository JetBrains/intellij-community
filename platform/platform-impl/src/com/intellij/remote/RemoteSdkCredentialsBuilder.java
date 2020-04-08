// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoteSdkCredentialsBuilder {
  private String myInterpreterPath = null;
  private static final String myHelpersPath = null;
  private RemoteCredentials myRemoteCredentials = null;
  private static final String myHelpersDefaultDirName = ".idea_helpers";


  public RemoteSdkCredentials build() {
    RemoteSdkCredentials result = new RemoteSdkCredentialsHolder(myHelpersDefaultDirName);

    if (myRemoteCredentials != null) {
      copyCredentials(myRemoteCredentials, result);
    }

    if (myInterpreterPath != null) {
      result.setInterpreterPath(myInterpreterPath);
    }

    if (myHelpersPath != null) {
      result.setHelpersPath(myHelpersPath);
    }

    return result;
  }


  public static void copyRemoteSdkCredentials(@NotNull RemoteSdkCredentials data, @NotNull RemoteSdkCredentials copyTo) {
    copyCredentials(data, copyTo);

    copyTo.setInterpreterPath(data.getInterpreterPath());
    copyTo.setRunAsRootViaSudo(data.isRunAsRootViaSudo());
    copyTo.setHelpersPath(data.getHelpersPath());

    copyTo.setHelpersVersionChecked(data.isHelpersVersionChecked());
  }

  public static void copyCredentials(@NotNull RemoteCredentials data, @NotNull MutableRemoteCredentials copyTo) {
    copyTo.setHost(data.getHost());
    copyTo.setLiteralPort(data.getLiteralPort());//then port is copied
    copyTo.setUserName(data.getUserName());
    copyTo.setPassword(data.getPassword());
    copyTo.setPrivateKeyFile(data.getPrivateKeyFile());
    copyTo.setPassphrase(data.getPassphrase());
    copyTo.setAuthType(data.getAuthType());

    copyTo.setStorePassword(data.isStorePassword());
    copyTo.setStorePassphrase(data.isStorePassphrase());
  }

  public RemoteSdkCredentialsBuilder withCredentials(@Nullable RemoteCredentials remoteCredentials) {
    myRemoteCredentials = remoteCredentials;
    return this;
  }

  public RemoteSdkCredentialsBuilder withInterpreterPath(String interpreterPath) {
    myInterpreterPath = interpreterPath;
    return this;
  }
}
