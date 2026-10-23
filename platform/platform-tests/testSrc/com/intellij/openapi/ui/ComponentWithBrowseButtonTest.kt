// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component.BaselineResizeBehavior
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JTextField

class ComponentWithBrowseButtonTest {

  private class ChildWithBaseline : JPanel() {
    var requestedHeight = -1

    override fun getBaseline(width: Int, height: Int): Int {
      requestedHeight = height
      return 10
    }

    override fun getBaselineResizeBehavior(): BaselineResizeBehavior = BaselineResizeBehavior.CONSTANT_ASCENT
  }

  @Test
  fun `baseline comes from the child component`() {
    val child = ChildWithBaseline()
    val component = ComponentWithBrowseButton(child) {}
    component.border = BorderFactory.createEmptyBorder(3, 5, 7, 9)

    assertEquals(3 + 10, component.getBaseline(200, 50))
    assertEquals(50 - 3 - 7, child.requestedHeight)
    assertEquals(BaselineResizeBehavior.CONSTANT_ASCENT, component.baselineResizeBehavior)
  }

  @Test
  fun `no baseline when the child has none`() {
    val component = ComponentWithBrowseButton(JPanel()) {}

    assertEquals(-1, component.getBaseline(200, 50))
  }

  @Test
  fun `text field has a baseline`() {
    val component = ComponentWithBrowseButton(JTextField()) {}

    assertTrue(component.getBaseline(200, 50) > 0)
  }
}
