// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

/**
 * Describes conditions under which a {@link RuntimeModuleDescriptor} must be loaded.
 * @see IncludedRuntimeModule#getImportance()
 */
public enum ModuleImportance {
  /**
   * The module provides essential user-visible functionality, must be always loaded.
   */
  FUNCTIONAL,

  /**
   * The module provides optional user-visible functionality, will be loaded if all dependencies are available.
   */
  OPTIONAL,

  /**
   * The module provides code which can be reused in other modules rather when user-visible functionality, it will be loaded only if some 
   * {@link #FUNCTIONAL} or {@link #OPTIONAL} module depends on it.
   */
  SERVICE
}
