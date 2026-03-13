// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization;

import com.intellij.platform.runtime.repository.IncludedRuntimeModule;
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule;
import com.intellij.platform.runtime.repository.impl.IncludedRuntimeModuleImpl;
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RawIncludedRuntimeModule {
  private final RuntimeModuleId myModuleId;
  private final RuntimeModuleLoadingRule myLoadingRule;
  private final @Nullable RuntimeModuleId myRequiredIfAvailableId;

  @ApiStatus.Internal
  public RawIncludedRuntimeModule(@NotNull RuntimeModuleId moduleId, @NotNull RuntimeModuleLoadingRule loadingRule, @Nullable RuntimeModuleId requiredIfAvailableId) {
    myModuleId = moduleId;
    myLoadingRule = loadingRule;
    myRequiredIfAvailableId = requiredIfAvailableId;
  }

  public @NotNull RuntimeModuleId getModuleId() {
    return myModuleId;
  }

  public @NotNull RuntimeModuleLoadingRule getLoadingRule() {
    return myLoadingRule;
  }

  public @Nullable RuntimeModuleId getRequiredIfAvailableId() {
    return myRequiredIfAvailableId;
  }

  @Override
  public String toString() {
    return "RawIncludedRuntimeModule{moduleId=" + myModuleId + '}';
  }

  public @Nullable IncludedRuntimeModule resolve(@NotNull RuntimeModuleRepository repository) {
    RuntimeModuleDescriptor descriptor;
    if (getLoadingRule() == RuntimeModuleLoadingRule.REQUIRED || getLoadingRule() == RuntimeModuleLoadingRule.EMBEDDED) {
      descriptor = repository.getModule(getModuleId());
    }
    else {
      //todo print something to the log if optional module is missing
      descriptor = repository.resolveModule(getModuleId()).getResolvedModule();
    }
    if (descriptor != null) {
      return new IncludedRuntimeModuleImpl(descriptor, myLoadingRule);
    }
    return null;
  }
}
