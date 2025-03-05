// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.impl;

import com.intellij.platform.runtime.product.ProductMode;
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class ProductModeMatcher {
  private final @NotNull String myIncompatibleRootModule;
  private final Map<String, Boolean> myCache;

  public ProductModeMatcher(@NotNull ProductMode productMode) {
    myIncompatibleRootModule = ProductModes.getIncompatibleRootModule(productMode).getStringId();
    myCache = new HashMap<>();
  }

  public boolean matches(@NotNull RuntimeModuleDescriptor moduleDescriptor) {
    String stringId = moduleDescriptor.getModuleId().getStringId();
    Boolean cached = myCache.get(stringId);
    if (cached != null) return cached;
    
    boolean matches;
    if (myIncompatibleRootModule.equals(stringId)) {
      matches = false;
    }
    else {
      myCache.put(stringId, true); //this is needed to prevent StackOverflowError in case of circular dependencies
      //noinspection SSBasedInspection
      matches = moduleDescriptor.getDependencies().stream().allMatch(this::matches);
    }
    myCache.put(stringId, matches);
    return matches;
  }
}
