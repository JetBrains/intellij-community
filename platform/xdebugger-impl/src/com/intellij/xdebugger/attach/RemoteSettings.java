/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xdebugger.attach;

import com.intellij.remote.RemoteSdkCredentials;
import org.jetbrains.annotations.NotNull;

public class RemoteSettings implements AttachSettings<RemoteSdkCredentials> {
  @NotNull private RemoteSdkCredentials myCredentials;
  @NotNull private XRemoteProcessListProvider myProvider;

  public RemoteSettings(@NotNull RemoteSdkCredentials credentials, @NotNull XRemoteProcessListProvider provider) {
    myCredentials = credentials;
    myProvider = provider;
  }

  @NotNull
  @Override
  public RemoteSdkCredentials getInfo() {
    return myCredentials;
  }

  @NotNull
  public XRemoteProcessListProvider getProvider() {
    return myProvider;
  }

  @NotNull
  @Override
  public String getText() {
    return myCredentials.getHost() + "@" + myCredentials.getPort();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RemoteSettings settings = (RemoteSettings)o;

    if (!myCredentials.equals(settings.myCredentials)) return false;
    if (!myProvider.equals(settings.myProvider)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myCredentials.hashCode();
    result = 31 * result + myProvider.hashCode();
    return result;
  }
}