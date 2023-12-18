// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.testFramework.TestApplicationManager
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import org.junit.Before
import org.junit.Test
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuilderTest {

  private val DEFAULT_GAP = JBUIScale.scale(4)

  @Before
  fun before() {
    TestApplicationManager.getInstance()
  }

  @Test
  fun testAccessibility() {
    val list = JList<Int>()
    val renderer = listCellRenderer<Int> {
      text("Hello")
      text("World $value")
    }

    assertEquals("Hello, World 1",
                 renderer.getListCellRendererComponent(list, 1, 0, false, false).accessibleContext.accessibleName)
    assertEquals("Hello, World 2",
                 renderer.getListCellRendererComponent(list, 2, 0, false, false).accessibleContext.accessibleName)
  }

  @Test
  fun testGaps() {
    val list = JList<Int>()
    val renderer = listCellRenderer<Int> {
      text("A")
      gap(LcrRow.Gap.DEFAULT)
      text("B")
      gap(LcrRow.Gap.NONE)
      text("C")
    }

    val component = renderer.getListCellRendererComponent(list, 1, 0, false, false)
    setRendererSize(component, 100, 100)
    val a = findLabel(component, "A")
    val b = findLabel(component, "B")
    val c = findLabel(component, "C")

    assertTrue(a.x >= 0)
    assertEquals(a.x + a.width + DEFAULT_GAP, b.x)
    assertEquals(b.x + b.width, c.x)
  }

  private fun setRendererSize(component: Component, width: Int, height: Int) {
    component.setBounds(0, 0, width, height)
    UIUtil.uiTraverser(component).forEach { it.doLayout() }
  }

  private fun findLabel(root: Component, text: String): JLabel {
    val result = UIUtil.uiTraverser(root).filter { it is JLabel && it.text == text }.toList()
    assertEquals(result.size, 1)
    return result[0] as JLabel
  }
}