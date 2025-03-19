// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product;

import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;

/**
 * Describes conditions under which a {@link RuntimeModuleDescriptor} must be loaded.
 * Meaning of the entries is the same as entries in {@link com.intellij.ide.plugins.ModuleLoadingRule} for content modules in plugins, but
 * here it applies not only to plugin modules, but to the platform modules as well.
 * In the future we can reuse this enum instead of having two copies.
 * @see IncludedRuntimeModule#getLoadingRule()
 */
public enum RuntimeModuleLoadingRule {
  /**
   * The module provides essential user-visible functionality, must be always loaded.
   */
  REQUIRED,

  /**
   * The module provides optional user-visible functionality, will be loaded if all dependencies are available and skipped otherwise.
   */
  OPTIONAL,

  /**
   * The module provides code which can be reused in other modules rather when user-visible functionality, it will be loaded only if some 
   * {@link #REQUIRED} or {@link #OPTIONAL} module depends on it.
   */
  ON_DEMAND
}
