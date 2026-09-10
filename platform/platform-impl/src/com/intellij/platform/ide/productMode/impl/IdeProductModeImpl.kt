// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.productMode.impl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.productMode.ProductMode

internal class IdeProductModeImpl : IdeProductMode {
  override val currentMode: ProductMode
    get() = PluginManagerCore.getPluginSet().initContext.productMode
}
