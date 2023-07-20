// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Describes modules included in the product. The modules are specified in product-modules.xml file, which is loaded by 
 * {@link com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization#loadProductModules} method.
 */
public interface ProductModules {
  /**
   * Returns description of the main module group. Modules from this group are always enabled.
   */
  @NotNull RuntimeModuleGroup getMainModuleGroup();

  /**
   * Returns description of module groups corresponding to the bundled plugins. Modules from these groups may be disabled if the corresponding
   * plugin is disabled by the user.
   */
  @NotNull List<@NotNull RuntimeModuleGroup> getBundledPluginModuleGroups();
}
