// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ModuleEx extends Module {
  default void init(@Nullable Runnable beforeComponentCreation) {
    if (beforeComponentCreation != null) {
      beforeComponentCreation.run();
    }
  }

  default void moduleAdded(List<? super ModuleComponent> oldComponents) {
  }

  default void projectClosed() {
  }

  default void rename(@NotNull String newName, boolean notifyStorage) {
  }

  void clearScopesCache();

  /**
   * @return true if this module can store settings in its IComponentStore
   */
  @ApiStatus.Internal
  default boolean canStoreSettings() {
    return true;
  }

  @ApiStatus.Internal
  @NotNull MessageBus getDeprecatedModuleLevelMessageBus();
}
