// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product

/**
 * Describes modules included in the product. The modules are specified in a `product-modules.xml` file, which is loaded by
 * [com.intellij.platform.runtime.product.serialization.ProductModulesSerialization.loadProductModules] method.
 */
interface ProductModules {
  /**
   * Returns a description of the main module group. Modules from this group are always enabled.
   */
  val mainModuleGroup: RuntimeModuleGroup

  /**
   * Returns description of module groups corresponding to the bundled plugins. Modules from these groups may be disabled if the corresponding
   * plugin is disabled by the user.
   */
  val bundledPluginModuleGroups: List<PluginModuleGroup>
}
