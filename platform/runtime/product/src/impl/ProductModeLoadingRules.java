// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.impl;

import com.intellij.platform.runtime.product.ProductMode;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.platform.runtime.product.ProductMode.*;

/**
 * This class is used to determine whether a module should be loaded based on the current {@link ProductMode}.
 * Currently, this is determined by the presence of specific modules in dependencies. We can change this and require to specify modes 
 * a module is compatible with explicitly in the future.
 */
@ApiStatus.Internal
public final class ProductModeLoadingRules {
  private ProductModeLoadingRules() {
  }

  /**
   * Returns the modules which presence in the dependencies indicates that a module isn't compatible with the given {@code mode}.
   */
  public static @NotNull List<RuntimeModuleId> getIncompatibleRootModules(@NotNull ProductMode mode) {
    if (mode.equals(FRONTEND)) {
      return List.of(RuntimeModuleId.module("intellij.platform.backend"),
                     RuntimeModuleId.module("intellij.platform.jps.build"),
                     RuntimeModuleId.module("intellij.platform.jps.build.dependencyGraph"));
    }
    else if (mode.equals(MONOLITH)) {
      //currently we use the same modules in 'backend' and 'monolith' modes, in the future we may disable some UI-only modules in 'backend' mode
      return List.of(RuntimeModuleId.module("intellij.platform.frontend.split"));
    }
    else if (mode.equals(BACKEND)) {
      return List.of(RuntimeModuleId.module("intellij.platform.frontend.split"));
    }
    else {
      throw new AssertionError("Unexpected value: " + mode);
    }
  }
}
