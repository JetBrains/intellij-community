// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product

import com.intellij.platform.runtime.repository.RuntimeModuleId

/**
 * Describes modules included in the product. The modules are specified in a `product-modules.xml` file, which is loaded by
 * [com.intellij.platform.runtime.product.serialization.ProductModulesSerialization.loadProductModules] method.
 */
interface ProductModules {

  /**
   * Returns IDs of modules containing `META-INF/plugin.xml` files of the bundled plugins.
   */
  val bundledPluginDescriptorModules: List<RuntimeModuleId>
}
