// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization;

import com.intellij.platform.runtime.repository.*;
import com.intellij.platform.runtime.repository.impl.IncludedRuntimeModuleImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class RawIncludedRuntimeModule {
  private final RuntimeModuleId myModuleId;
  private final ModuleImportance myImportance;
  private final Set<RuntimeModuleScope> myScopes;

  public RawIncludedRuntimeModule(@NotNull RuntimeModuleId moduleId, @NotNull ModuleImportance importance,
                                  @NotNull Set<RuntimeModuleScope> scopes) {
    myModuleId = moduleId;
    myImportance = importance;
    myScopes = scopes;
  }

  public @NotNull RuntimeModuleId getModuleId() {
    return myModuleId;
  }

  public @NotNull ModuleImportance getImportance() {
    return myImportance;
  }

  public @NotNull Set<RuntimeModuleScope> getScopes() {
    return myScopes;
  }

  @Override
  public String toString() {
    return "RawIncludedRuntimeModule{moduleId=" + myModuleId + '}';
  }

  public @Nullable IncludedRuntimeModule resolve(@NotNull RuntimeModuleRepository repository) {
    RuntimeModuleDescriptor descriptor;
    if (getImportance() == ModuleImportance.FUNCTIONAL) {
      descriptor = repository.getModule(getModuleId());
    }
    else {
      //todo print something to the log if optional module is missing
      descriptor = repository.resolveModule(getModuleId()).getResolvedModule();
    }
    if (descriptor != null) {
      return new IncludedRuntimeModuleImpl(descriptor, myImportance, myScopes);
    }
    return null;
  }
}
