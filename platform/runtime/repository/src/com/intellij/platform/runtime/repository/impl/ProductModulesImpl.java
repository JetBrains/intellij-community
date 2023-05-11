// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.ProductModules;
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.IncludedRuntimeModule;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ProductModulesImpl implements ProductModules {
  private final String myDebugName;
  private final List<IncludedRuntimeModule> myRootPlatformModules;
  private final List<RuntimeModuleDescriptor> myBundledPluginModules;

  public ProductModulesImpl(@NotNull @NonNls String debugName, @NotNull List<IncludedRuntimeModule> rootPlatformModules,
                            @NotNull List<RuntimeModuleDescriptor> bundledPluginModules) {
    myDebugName = debugName;
    myRootPlatformModules = rootPlatformModules;
    myBundledPluginModules = bundledPluginModules;
  }

  @Override
  public @NotNull List<IncludedRuntimeModule> getRootPlatformModules() {
    return myRootPlatformModules;
  }

  @Override
  public @NotNull List<RuntimeModuleDescriptor> getBundledPluginMainModules() {
    return myBundledPluginModules;
  }

  @Override
  public String toString() {
    return "ProductModules{debugName=" + myDebugName + '}';
  }
}
