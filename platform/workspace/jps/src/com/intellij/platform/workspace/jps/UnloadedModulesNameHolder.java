// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface UnloadedModulesNameHolder {

  UnloadedModulesNameHolder DUMMY = new UnloadedModulesNameHolder() {

    @Override
    public boolean isUnloaded(@NotNull String name) {
      return false;
    }

    @Override
    public boolean hasUnloaded() {
      return false;
    }
  };

  boolean isUnloaded(@NotNull String name);

  boolean hasUnloaded();
}
