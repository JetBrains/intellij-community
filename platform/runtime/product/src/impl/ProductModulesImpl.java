// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.impl;

import com.intellij.platform.runtime.product.PluginModuleGroup;
import com.intellij.platform.runtime.product.ProductModules;
import com.intellij.platform.runtime.product.RuntimeModuleGroup;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ProductModulesImpl implements ProductModules {
  private final String myDebugName;
  private final MainRuntimeModuleGroup myMainGroup;
  private final List<PluginModuleGroup> myBundledPluginGroups;

  public ProductModulesImpl(@NotNull @NonNls String debugName, MainRuntimeModuleGroup mainGroup,
                            @NotNull List<PluginModuleGroup> bundledPluginGroups) {
    myDebugName = debugName;
    myMainGroup = mainGroup;
    myBundledPluginGroups = bundledPluginGroups;
  }

  @Override
  public @NotNull RuntimeModuleGroup getMainModuleGroup() {
    return myMainGroup;
  }

  @Override
  public @NotNull List<@NotNull PluginModuleGroup> getBundledPluginModuleGroups() {
    return myBundledPluginGroups;
  }

  @Override
  public String toString() {
    return "ProductModules{debugName=" + myDebugName + '}';
  }
}
