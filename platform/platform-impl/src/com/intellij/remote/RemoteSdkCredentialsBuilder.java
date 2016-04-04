/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.remote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class RemoteSdkCredentialsBuilder {
  private String myInterpreterPath = null;
  private String myHelpersPath = null;
  private RemoteCredentials myRemoteCredentials = null;
  private String myHelpersDefaultDirName = ".idea_helpers";


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
    copyTo.setHelpersPath(data.getHelpersPath());

    copyTo.setHelpersVersionChecked(data.isHelpersVersionChecked());
    copyTo.setRemoteRoots(data.getRemoteRoots());
  }

  public static void copyCredentials(@NotNull RemoteCredentials data, @NotNull MutableRemoteCredentials copyTo) {
    copyTo.setHost(data.getHost());
    copyTo.setLiteralPort(data.getLiteralPort());//then port is copied
    copyTo.setAnonymous(data.isAnonymous());
    copyTo.setUserName(data.getUserName());
    copyTo.setPassword(data.getPassword());
    copyTo.setPrivateKeyFile(data.getPrivateKeyFile());
    copyTo.setKnownHostsFile(data.getKnownHostsFile());
    copyTo.setPassphrase(data.getPassphrase());
    copyTo.setUseKeyPair(data.isUseKeyPair());

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
