// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.serialization;

import com.intellij.platform.runtime.product.IncludedRuntimeModule;
import com.intellij.platform.runtime.product.RuntimeModuleLoadingRule;
import com.intellij.platform.runtime.repository.*;
import com.intellij.platform.runtime.product.impl.IncludedRuntimeModuleImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RawIncludedRuntimeModule {
  private final RuntimeModuleId myModuleId;
  private final RuntimeModuleLoadingRule myLoadingRule;

  @ApiStatus.Internal
  public RawIncludedRuntimeModule(@NotNull RuntimeModuleId moduleId, @NotNull RuntimeModuleLoadingRule loadingRule) {
    myModuleId = moduleId;
    myLoadingRule = loadingRule;
  }

  public @NotNull RuntimeModuleId getModuleId() {
    return myModuleId;
  }

  public @NotNull RuntimeModuleLoadingRule getLoadingRule() {
    return myLoadingRule;
  }

  @Override
  public String toString() {
    return "RawIncludedRuntimeModule{moduleId=" + myModuleId + '}';
  }

  public @Nullable IncludedRuntimeModule resolve(@NotNull RuntimeModuleRepository repository) {
    RuntimeModuleDescriptor descriptor;
    if (getLoadingRule() == RuntimeModuleLoadingRule.REQUIRED) {
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
