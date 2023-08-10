// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.ProductModules;
import com.intellij.platform.runtime.repository.RuntimeModuleGroup;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ProductModulesImpl implements ProductModules {
  private final String myDebugName;
  private final MainRuntimeModuleGroup myMainGroup;
  private final List<RuntimeModuleGroup> myBundledPluginGroups;

  public ProductModulesImpl(@NotNull @NonNls String debugName, MainRuntimeModuleGroup mainGroup,
                            @NotNull List<RuntimeModuleGroup> bundledPluginGroups) {
    myDebugName = debugName;
    myMainGroup = mainGroup;
    myBundledPluginGroups = bundledPluginGroups;
  }

  @Override
  public @NotNull RuntimeModuleGroup getMainModuleGroup() {
    return myMainGroup;
  }

  @Override
  public @NotNull List<@NotNull RuntimeModuleGroup> getBundledPluginModuleGroups() {
    return myBundledPluginGroups;
  }

  @Override
  public String toString() {
    return "ProductModules{debugName=" + myDebugName + '}';
  }
}
