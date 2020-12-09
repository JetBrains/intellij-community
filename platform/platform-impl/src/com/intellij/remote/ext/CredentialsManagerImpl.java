// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ext;

import com.intellij.remote.CredentialsType;
import com.intellij.remote.RemoteSdkAdditionalData;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class CredentialsManagerImpl extends CredentialsManager {
  @Override
  public List<CredentialsType<?>> getAllTypes() {
    return CredentialsType.EP_NAME.getExtensionList();
  }

  @Override
  public void loadCredentials(String interpreterPath, @Nullable Element element, RemoteSdkAdditionalData data) {
    for (CredentialsType type : CredentialsType.EP_NAME.getExtensionList()) {
      if (type.hasPrefix(interpreterPath)) {
        Object credentials = type.createCredentials();
        type.getHandler(credentials).load(element);
        data.setCredentials(type.getCredentialsKey(), credentials);
        return;
      }
    }

    UnknownCredentialsHolder credentials = CredentialsType.UNKNOWN.createCredentials();
    credentials.setSdkId(interpreterPath);
    credentials.load(element);
    data.setCredentials(CredentialsType.UNKNOWN_CREDENTIALS, credentials);
  }
}
