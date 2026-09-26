// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Represents a module included in [RuntimePluginHeader]
public interface IncludedRuntimeModule {
  @NotNull RuntimeModuleId getModuleId();

  @NotNull RuntimeModuleLoadingRule getLoadingRule();

  @Nullable RuntimeModuleId getRequiredIfAvailableId();
}
