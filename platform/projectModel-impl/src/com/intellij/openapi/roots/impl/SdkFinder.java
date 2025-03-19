// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SdkFinder {
  public static final ExtensionPointName<SdkFinder> EP_NAME = ExtensionPointName.create("com.intellij.sdkFinder");

  public @Nullable Sdk findSdk(@NotNull String name, @NotNull String sdkType) {
    return null;
  }
}
