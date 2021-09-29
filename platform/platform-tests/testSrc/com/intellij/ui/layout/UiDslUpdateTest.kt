// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class UiDslUpdateTest : BasePlatformTestCase() {
  fun testRowVisible() {
    lateinit var theRow: Row
    val p = panel {
      row {
        theRow = this
        label("A")
        label("B").visible(false)
      }
    }
    assertTrue(p.components[0].isVisible)
    assertFalse(p.components[1].isVisible)

    theRow.visible = false
    assertFalse(p.components[0].isVisible)
    assertFalse(p.components[1].isVisible)

    theRow.visible = true
    assertTrue(p.components[0].isVisible)
    assertFalse(p.components[1].isVisible)
  }
}
