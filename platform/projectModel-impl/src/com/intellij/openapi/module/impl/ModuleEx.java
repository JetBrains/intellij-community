// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.Module;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ModuleEx extends Module {
  default void init(@Nullable Runnable beforeComponentCreation) {
    if (beforeComponentCreation != null) {
      beforeComponentCreation.run();
    }
  }

  default void moduleAdded() {
  }

  default void projectOpened() {
  }

  default void projectClosed() {
  }

  default void rename(@NotNull String newName, boolean notifyStorage) {
  }

  void clearScopesCache();

  default long getOptionsModificationCount() {
    return 0;
  }

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
