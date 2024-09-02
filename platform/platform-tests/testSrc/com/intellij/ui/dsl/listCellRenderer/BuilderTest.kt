// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.icons.AllIcons
import com.intellij.testFramework.TestApplicationManager
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.listCellRenderer.impl.LcrRowImpl
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import org.junit.Before
import org.junit.Test
import java.awt.Component
import javax.swing.JList
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuilderTest {

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
    val a = findTextComponent(component, "A")
    val b = findTextComponent(component, "B")
    val c = findTextComponent(component, "C")

    assertTrue(a.x >= 0)
    assertEquals(a.x + a.width + JBUIScale.scale(LcrRowImpl.DEFAULT_GAP), b.x)
    assertEquals(b.x + b.width, c.x)
  }

  @Test
  fun testGetCopyText() {
    val copyText = "Copy Text"

    assertEquals(copyText, getCopyText {
      text(copyText)
    })
    assertEquals(copyText, getCopyText {
      icon(AllIcons.General.Add)
      text(copyText)
    })
    assertEquals(copyText, getCopyText {
      icon(AllIcons.General.Add)
      text("")
      text(copyText)
    })
    assertEquals(copyText, getCopyText {
      text(copyText)
      text("Secondary text")
    })
  }

  private fun getCopyText(init: LcrRow<Unit>.() -> Unit): String? {
    val renderer = listCellRenderer(init)
    val list = JBList<Unit>()
    return (renderer.getListCellRendererComponent(list, null, 0, true, false) as KotlinUIDslRendererComponent).getCopyText()
  }

  private fun setRendererSize(component: Component, width: Int, height: Int) {
    component.setBounds(0, 0, width, height)
    UIUtil.uiTraverser(component).forEach { it.doLayout() }
  }

  private fun findTextComponent(root: Component, text: String): SimpleColoredComponent {
    val result = UIUtil.uiTraverser(root).filter { it is SimpleColoredComponent && it.getCharSequence(false).toString() == text }.toList()
    assertEquals(result.size, 1)
    return result[0] as SimpleColoredComponent
  }
}