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
package com.intellij.remotesdk;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class RemoteSdkDataBuilder {
  private String myInterpreterPath = null;
  private String myHelpersPath = null;
  private RemoteCredentials myRemoteCredentials = null;
  private String myHelpersDefaultDirName = ".idea_helpers";


  public RemoteSdkData build() {
    RemoteSdkData result = new RemoteSdkDataHolder(myHelpersDefaultDirName);

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


  public static void copyRemoteSdkData(@NotNull RemoteSdkData data, @NotNull RemoteSdkData copyTo) {
    copyCredentials(data, copyTo);

    copyTo.setInterpreterPath(data.getInterpreterPath());
    copyTo.setHelpersPath(data.getHelpersPath());

    copyTo.setHelpersVersionChecked(data.isHelpersVersionChecked());
    copyTo.setRemoteRoots(data.getRemoteRoots());
  }

  public static void copyCredentials(RemoteCredentials data, MutableRemoteCredentials copyTo) {
    copyTo.setHost(data.getHost());
    copyTo.setPort(data.getPort());
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

  public RemoteSdkDataBuilder withCredentials(@Nullable RemoteCredentials remoteCredentials) {
    myRemoteCredentials = remoteCredentials;
    return this;
  }

  public RemoteSdkDataBuilder withInterpreterPath(String interpreterPath) {
    myInterpreterPath = interpreterPath;
    return this;
  }
}
