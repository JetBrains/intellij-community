// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ext;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.remote.CredentialsType;
import com.intellij.remote.RemoteCredentials;
import org.jetbrains.annotations.Nls;

public abstract class CredentialsTypeEx<T> extends CredentialsType<T> {

  public static final ExtensionPointName<CredentialsTypeEx> EP_NAME = ExtensionPointName.create("com.intellij.remote.credentialsType");

  protected CredentialsTypeEx(@Nls(capitalization = Nls.Capitalization.Title) String name, String prefix) {
    super(name, prefix);
  }

  public abstract RemoteCredentials createRemoteCredentials(T credentials);

  public abstract boolean useRemoteCredentials();

  public abstract boolean isBrowsingAvailable();
}
