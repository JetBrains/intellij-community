// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote.ext;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO: (next) rename to wrapper?
public interface RemoteCredentialsHandler {

  @NotNull String getId();

  void save(@NotNull Element rootElement);

  String getPresentableDetails(String interpreterPath);

  void load(@Nullable Element rootElement) throws CannotLoadCredentialsException;
}
