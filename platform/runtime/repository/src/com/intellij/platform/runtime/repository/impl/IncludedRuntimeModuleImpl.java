// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.IncludedRuntimeModule;
import com.intellij.platform.runtime.repository.ModuleImportance;
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import org.jetbrains.annotations.NotNull;

public final class IncludedRuntimeModuleImpl implements IncludedRuntimeModule {
  private final RuntimeModuleDescriptor myModuleDescriptor;
  private final ModuleImportance myImportance;

  public IncludedRuntimeModuleImpl(@NotNull RuntimeModuleDescriptor moduleDescriptor,
                                   @NotNull ModuleImportance importance) {
    myModuleDescriptor = moduleDescriptor;
    myImportance = importance;
  }

  @Override
  public @NotNull RuntimeModuleDescriptor getModuleDescriptor() {
    return myModuleDescriptor;
  }

  @Override
  public @NotNull ModuleImportance getImportance() {
    return myImportance;
  }

  @Override
  public String toString() {
    return "IncludedRuntimeModule{moduleId=" + myModuleDescriptor.getModuleId().getStringId() + '}';
  }
}
