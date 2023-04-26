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
   * Returns modules which together with their dependencies constitute the platform part of the product.
   */
  @NotNull List<@NotNull IncludedRuntimeModule> getRootPlatformModules();

  /**
   * Returns main modules (containing META-INF/plugin.xml file) of bundled plugins. 
   */
  @NotNull List<@NotNull RuntimeModuleDescriptor> getBundledPluginMainModules();
}
