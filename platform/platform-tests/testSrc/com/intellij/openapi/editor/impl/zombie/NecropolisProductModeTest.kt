// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.replaceService

internal class NecropolisProductModeTest : LightPlatformTestCase() {

  fun testLightModeHasNoNecropolis() {
    setProductMode(ProductMode.LIGHT)
    assertNull(Necropolis.getInstance(project))
  }

  fun testLightModeWithRdConnectionHasNoNecropolis() {
    setProductMode(ProductMode.LIGHT_WITH_RD_CONNECTION)
    assertNull(Necropolis.getInstance(project))
  }

  fun testMonolithModeHasNecropolis() {
    setProductMode(ProductMode.MONOLITH)
    assertNotNull(Necropolis.getInstance(project))
  }

  private fun setProductMode(mode: ProductMode) {
    ApplicationManager.getApplication().replaceService(IdeProductMode::class.java, object : IdeProductMode {
      override val currentMode: ProductMode = mode
    }, testRootDisposable)
  }
}
