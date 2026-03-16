// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.impl

import com.intellij.platform.runtime.product.PluginModuleGroup
import com.intellij.platform.runtime.product.ProductModules
import com.intellij.platform.runtime.repository.RuntimeModuleId
import org.jetbrains.annotations.NonNls

class ProductModulesImpl(
  private val debugName: @NonNls String,
  override val mainModuleGroup: MainRuntimeModuleGroup,
  override val bundledPluginModuleGroups: List<PluginModuleGroup>,
  override val notLoadedBundledPluginModules: Map<RuntimeModuleId, List<RuntimeModuleId>>,
) : ProductModules {
  override fun toString(): String {
    return "ProductModules{debugName=$debugName}"
  }
}
