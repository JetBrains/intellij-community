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

  /**
   * @deprecated Just inline this method.
   */
  @Deprecated
  public static void copyCredentials(@NotNull RemoteCredentials data, @NotNull MutableRemoteCredentials copyTo) {
    RemoteCredentialsHolder.copyRemoteCredentials(data, copyTo);
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
