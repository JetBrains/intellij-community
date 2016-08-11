/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.remote.ext;

import com.intellij.remote.CredentialsType;
import com.intellij.remote.RemoteSdkAdditionalData;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CredentialsManagerImpl extends CredentialsManager {

  @Override
  public List<CredentialsType> getAllTypes() {
    List<CredentialsType> result = new ArrayList<>();
    result.add(CredentialsType.SSH_HOST);
    result.add(CredentialsType.VAGRANT);
    result.add(CredentialsType.WEB_DEPLOYMENT);
    result.addAll(getExTypes());
    return result;
  }

  @Override
  public List<CredentialsTypeEx> getExTypes() {
    return Arrays.asList(CredentialsTypeEx.EP_NAME.getExtensions());
  }

  @Override
  public void loadCredentials(String interpreterPath, @Nullable Element element, RemoteSdkAdditionalData data) {
    for (CredentialsType type : getAllTypes()) {
      if (type.hasPrefix(interpreterPath)) {
        Object credentials = type.createCredentials();
        type.getHandler(credentials).load(element);
        data.setCredentials(type.getCredentialsKey(), credentials);
        return;
      }
    }
    final UnknownCredentialsHolder credentials = CredentialsType.UNKNOWN.createCredentials();
    credentials.setInterpreterPath(interpreterPath);
    credentials.load(element);
    data.setCredentials(CredentialsType.UNKNOWN_CREDENTIALS, credentials);
  }
}
