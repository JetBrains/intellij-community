// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import org.jetbrains.annotations.ApiStatus;

/**
 * Describes conditions under which a {@link RuntimeModuleDescriptor} must be loaded.
 * Meaning of the entries is the same as entries in {@link com.intellij.ide.plugins.ModuleLoadingRule} for content modules in plugins, but
 * here it applies not only to plugin modules, but to the platform modules as well.
 * In the future we can reuse this enum instead of having two copies.
 * @see com.intellij.platform.runtime.repository.IncludedRuntimeModule#getLoadingRule()
 */
public enum RuntimeModuleLoadingRule {
  /**
   * The module provides essential user-visible functionality, must be always loaded.
   */
  REQUIRED,
  
  /**
   * The same as {@link #REQUIRED}, but also specifies, that classes from the module must be loaded by the main classloader of the 
   * corresponding module group.
   */
  EMBEDDED,

  /**
   * The module provides optional user-visible functionality, will be loaded if all dependencies are available and skipped otherwise.
   */
  OPTIONAL,

  /**
   * The module provides code which can be reused in other modules rather when user-visible functionality, it will be loaded only if some 
   * {@link #REQUIRED} or {@link #OPTIONAL} module depends on it.
   */
  ON_DEMAND,

  /**
   * The same as {@link #EMBEDDED}, but also indicates that the module doesn't have an explicit XML descriptor.
   * Modules with this loading rule represent just a set of classes and resources included in the classpath of the main plugin classloader.
   */
  @ApiStatus.Internal
  IMPLICIT_EMBEDDED,
}
