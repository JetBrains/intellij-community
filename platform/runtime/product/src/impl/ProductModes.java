// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.impl;

import com.intellij.platform.runtime.product.ProductMode;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import org.jetbrains.annotations.NotNull;

/**
 * This class is used to determine whether a module should be loaded based on the current {@link ProductMode}.
 * Currently, this is determined by the presence of specific modules in dependencies. We can change this and require to specify modes 
 * a module is compatible with explicitly in the future.
 */
final class ProductModes {
  private ProductModes() {
  }

  /**
   * Returns the module which presence in the dependencies indicates that a module isn't compatible with the given {@code mode}.
   */
  public static @NotNull RuntimeModuleId getIncompatibleRootModule(@NotNull ProductMode mode) {
    switch (mode) {
      case FRONTEND: return RuntimeModuleId.module("intellij.platform.localIde");
      
      //this module doesn't exist yet, it will be added when we need to use it
      case LOCAL_IDE: return RuntimeModuleId.module("intellij.platform.frontend");

      //currently we use the same modules in 'backend' and 'localIde' modes, in the future we may disable some UI-only modules in 'backend' mode
      case BACKEND: return RuntimeModuleId.module("intellij.platform.frontend");
    }
    throw new AssertionError(mode);
  }
}
