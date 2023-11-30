// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.IncludedRuntimeModule;
import com.intellij.platform.runtime.repository.ModuleImportance;
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.RuntimeModuleScope;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class IncludedRuntimeModuleImpl implements IncludedRuntimeModule {
  private final RuntimeModuleDescriptor myModuleDescriptor;
  private final ModuleImportance myImportance;
  private final Set<RuntimeModuleScope> myScopes;

  public IncludedRuntimeModuleImpl(@NotNull RuntimeModuleDescriptor moduleDescriptor,
                                   @NotNull ModuleImportance importance,
                                   @NotNull Set<RuntimeModuleScope> scopes) {
    myModuleDescriptor = moduleDescriptor;
    myImportance = importance;
    myScopes = scopes;
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
  public @NotNull Set<RuntimeModuleScope> getScopes() {
    return myScopes;
  }

  @Override
  public String toString() {
    return "IncludedRuntimeModule{moduleId=" + myModuleDescriptor.getModuleId().getStringId() + '}';
  }
}
