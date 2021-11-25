// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ext;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO: (next) rename to wrapper?
public interface RemoteCredentialsHandler {

  @NotNull String getId();

  void save(@NotNull Element rootElement);

  String getPresentableDetails(String interpreterPath);

  void load(@Nullable Element rootElement) throws CredentialsCantBeLoaded;
}
