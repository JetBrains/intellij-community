// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This class is used to determine whether a module should be loaded based on the current {@link ProductMode}.
 * Currently, this is determined by the presence of specific modules in dependencies. We can change this and require to specify modes 
 * a module is compatible with explicitly in the future.
 */
@ApiStatus.Internal
public final class ProductModes {
  private ProductModes() {
  }

  /**
   * Returns the module which presence in the dependencies indicates that a module isn't compatible with the given {@code mode}.
   */
  public static @NotNull RuntimeModuleId getIncompatibleRootModule(@NotNull ProductMode mode) {
    switch (mode) {
      case FRONTEND: return RuntimeModuleId.module("intellij.platform.localIde");
      case LOCAL_IDE: return RuntimeModuleId.module("intellij.platform.frontend"); //this module doesn't exist yet, it will be added when we need to use it 
    }
    throw new AssertionError(mode);
  }
}
