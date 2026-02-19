// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.productMode.impl

import com.intellij.ide.plugins.ProductLoadingStrategy
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.runtime.product.ProductMode

internal class IdeProductModeImpl : IdeProductMode {
  override val currentMode: ProductMode by lazy {
    val modeId = ProductLoadingStrategy.strategy.currentModeId
    ProductMode.findById(modeId) ?: error("Unknown product mode: $modeId")
  }
}
