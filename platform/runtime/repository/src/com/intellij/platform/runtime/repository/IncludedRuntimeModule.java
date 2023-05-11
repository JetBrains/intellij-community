// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Describes a module included into the platform or a plugin.
 */
public interface IncludedRuntimeModule {
  @NotNull RuntimeModuleDescriptor getModuleDescriptor();

  /**
   * Returns instance describing conditions under which the module is loaded. 
   */
  @NotNull ModuleImportance getImportance();

  /**
   * Returns the set of scopes in which the module is used.
   */
  @NotNull Set<RuntimeModuleScope> getScopes();
}
