// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.IncludedRuntimeModule;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class IncludedRuntimeModuleImpl implements IncludedRuntimeModule {
  private final RuntimeModuleId myModuleId;
  private final RuntimeModuleLoadingRule myLoadingRule;
  private final @Nullable RuntimeModuleId myRequiredIfAvailableId;

  @ApiStatus.Internal
  public IncludedRuntimeModuleImpl(@NotNull RuntimeModuleId moduleId, @NotNull RuntimeModuleLoadingRule loadingRule, @Nullable RuntimeModuleId requiredIfAvailableId) {
    myModuleId = moduleId;
    myLoadingRule = loadingRule;
    myRequiredIfAvailableId = requiredIfAvailableId;
  }

  @Override
  public @NotNull RuntimeModuleId getModuleId() {
    return myModuleId;
  }

  @Override
  public @NotNull RuntimeModuleLoadingRule getLoadingRule() {
    return myLoadingRule;
  }

  @Override
  public @Nullable RuntimeModuleId getRequiredIfAvailableId() {
    return myRequiredIfAvailableId;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    IncludedRuntimeModuleImpl module = (IncludedRuntimeModuleImpl)o;
    return myModuleId.equals(module.myModuleId) && myLoadingRule == module.myLoadingRule
           && Objects.equals(myRequiredIfAvailableId, module.myRequiredIfAvailableId);
  }

  @Override
  public int hashCode() {
    return 31 * (31 * myModuleId.hashCode() + myLoadingRule.hashCode()) + Objects.hashCode(myRequiredIfAvailableId);
  }

  @Override
  public String toString() {
    return "IncludedRuntimeModule{moduleId=" + myModuleId + '}';
  }
}
