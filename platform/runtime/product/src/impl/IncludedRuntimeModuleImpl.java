// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.impl;

import com.intellij.platform.runtime.product.IncludedRuntimeModule;
import com.intellij.platform.runtime.product.RuntimeModuleLoadingRule;
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import org.jetbrains.annotations.NotNull;

public final class IncludedRuntimeModuleImpl implements IncludedRuntimeModule {
  private final RuntimeModuleDescriptor myModuleDescriptor;
  private final RuntimeModuleLoadingRule myLoadingRule;

  public IncludedRuntimeModuleImpl(@NotNull RuntimeModuleDescriptor moduleDescriptor, @NotNull RuntimeModuleLoadingRule loadingRule) {
    myModuleDescriptor = moduleDescriptor;
    myLoadingRule = loadingRule;
  }

  @Override
  public @NotNull RuntimeModuleDescriptor getModuleDescriptor() {
    return myModuleDescriptor;
  }

  @Override
  public @NotNull RuntimeModuleLoadingRule getLoadingRule() {
    return myLoadingRule;
  }

  @Override
  public String toString() {
    return "IncludedRuntimeModule{moduleId=" + myModuleDescriptor.getModuleId().getStringId() + '}';
  }
}
