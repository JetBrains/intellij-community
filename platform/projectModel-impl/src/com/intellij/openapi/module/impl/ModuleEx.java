// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface ModuleEx extends Module {
  default void init() {
  }

  default void markAsLoaded() {
  }

  default void rename(@NotNull String newName, boolean notifyStorage) {
  }

  void clearScopesCache();
}
