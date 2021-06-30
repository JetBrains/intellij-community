// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.ProjectTopics;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Project root changes.
 *
 * @see ProjectTopics#PROJECT_ROOTS
 */
@ApiStatus.OverrideOnly
public interface ModuleRootListener extends EventListener {
  
  default void beforeRootsChange(@NotNull ModuleRootEvent event) {
  }

  default void rootsChanged(@NotNull ModuleRootEvent event) {
  }
}
