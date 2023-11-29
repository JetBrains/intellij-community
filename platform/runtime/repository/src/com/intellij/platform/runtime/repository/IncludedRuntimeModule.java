// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import org.jetbrains.annotations.NotNull;

/**
 * Describes a module included in the product as part of some {@link RuntimeModuleGroup}.
 */
public interface IncludedRuntimeModule {
  @NotNull RuntimeModuleDescriptor getModuleDescriptor();

  /**
   * Returns instance describing conditions under which the module is loaded. 
   */
  @NotNull ModuleImportance getImportance();
}
