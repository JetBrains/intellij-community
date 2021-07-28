// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ext;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Irina.Chernushina on 7/29/2016.
 */
public class UnknownTypeRemoteCredentialHandler extends RemoteCredentialsHandlerBase<UnknownCredentialsHolder> {
  public UnknownTypeRemoteCredentialHandler(UnknownCredentialsHolder credentials) {
    super(credentials);
  }

  @Override
  public @NotNull String getId() {
    return getCredentials().getSdkId();
  }

  @Override
  public void save(@NotNull Element rootElement) {
    getCredentials().save(rootElement);
  }

  @Override
  public String getPresentableDetails(String interpreterPath) {
    return getId();
  }

  @Override
  public void load(@Nullable Element rootElement) {
    getCredentials().load(rootElement);
  }
}
