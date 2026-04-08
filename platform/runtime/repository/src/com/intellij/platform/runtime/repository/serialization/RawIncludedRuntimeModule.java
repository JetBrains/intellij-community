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
    RuntimeModuleId moduleId = getModuleId();
    if (moduleId.getNamespace().equals(RuntimeModuleId.DEFAULT_NAMESPACE) && repository.resolveModule(moduleId).getResolvedModule() == null) {
      /* there are cases when the same module is included as a content module in one product, and as JPS module to another,
         e.g., `intellij.kotlin.base.codeInsight.minimal` is included as a content module in 'com.intellij.kotlin.frontend' and as a JPS
         module in `org.jetbrains.kotlin` plugin; so until IJPL-240871 is implemented, let's try resolving with a different namespace */
      moduleId = RuntimeModuleId.contentModule(moduleId.getName(), RuntimeModuleId.LEGACY_JPS_MODULE_NAMESPACE);
    }
    if (getLoadingRule() == RuntimeModuleLoadingRule.REQUIRED || getLoadingRule() == RuntimeModuleLoadingRule.EMBEDDED) {
      descriptor = repository.getModule(moduleId);
    }
    else {
      //todo print something to the log if optional module is missing
      descriptor = repository.resolveModule(moduleId).getResolvedModule();
    }
    if (descriptor != null) {
      return new IncludedRuntimeModuleImpl(descriptor, myLoadingRule);
    }
    return null;
  }
}
