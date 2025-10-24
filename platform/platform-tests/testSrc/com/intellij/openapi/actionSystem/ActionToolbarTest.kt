// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.test.assertEquals
import kotlin.test.assertSame

@TestApplication
@RunInEdt(allMethods = false)
class ActionToolbarTest {
  @Test
  @RunMethodInEdt
  fun testReplaceRegularAction() {
    class A(name: String) : AnAction(name, name, null) {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }

    var first = true
    val group = object : ActionGroup() {
      val actions = arrayOf(A("1"), A("2"), A("3"))
      override fun getActionUpdateThread() = ActionUpdateThread.EDT
      override fun getChildren(e: AnActionEvent?) = if (first) actions else arrayOf(actions[0], A("22"), A("33"))
    }
    val toolbar = ActionToolbarImpl("Test", group, false)
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())
    toolbar.assertToolbarTexts("1", "2", "3")
    val button1 = toolbar.components[0]

    first = false
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())
    toolbar.assertToolbarTexts("1", "22", "33")
    assertSame(button1, toolbar.components[0], "First button must be reused")
  }

  @Test
  @RunMethodInEdt
  fun testReplaceReorderedCustomActions() {
    class A(name: String) : AnAction(name), CustomComponentAction {
      override fun actionPerformed(e: AnActionEvent) = Unit
      override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return ActionButton(this, presentation, place, ::Dimension)
      }
    }

    var first = true
    val group = object : ActionGroup() {
      val actions = arrayOf(A("1"), A("2"), A("3"))
      override fun getActionUpdateThread() = ActionUpdateThread.EDT
      override fun getChildren(e: AnActionEvent?) = if (first) actions else arrayOf(actions[0], actions[2], actions[1])
    }
    val toolbar = ActionToolbarImpl("Test", group, false)
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())
    toolbar.assertToolbarTexts("1", "2", "3")

    first = false
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())
    toolbar.assertToolbarTexts("1", "3", "2")
  }

  @Test
  @RunMethodInEdt
  fun testReplaceReorderedTextActions() {
    class A(name: String) : AnAction(name) {
      override fun actionPerformed(e: AnActionEvent) = Unit
      override fun update(e: AnActionEvent) {
        e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
        super.update(e)
      }
    }

    var first = true
    val group = object : ActionGroup() {
      val actions = arrayOf(A("1"), A("22222222"), A("3"))
      override fun getActionUpdateThread() = ActionUpdateThread.EDT
      override fun getChildren(e: AnActionEvent?) = if (first) actions else arrayOf(actions[0], actions[2], actions[1])
    }
    val toolbar = ActionToolbarImpl("Test", group, false)

    toolbar.layoutStrategy = object : ToolbarLayoutStrategy {
      override fun calculateBounds(toolbar: ActionToolbar): List<Rectangle> = buildList {
        var x = 0
        for (component in toolbar.component.components) {
          val width = (component as ActionButton).action.templateText.length * 10
          add(Rectangle(x, 0, width, 10))
          x += width
        }
      }

      override fun calcPreferredSize(toolbar: ActionToolbar): Dimension = Dimension(0, 0)

      override fun calcMinimumSize(toolbar: ActionToolbar): Dimension = Dimension(0, 0)
    }

    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())
    toolbar.doLayout()
    toolbar.assertToolbarTexts("1", "22222222", "3")
    toolbar.components.map { it.width }.let {
      assertEquals(listOf(10, 80, 10), it, "Width invariant fails")
    }

    first = false
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())
    toolbar.assertToolbarTexts("1", "3", "22222222")
    toolbar.components.map { it.width }.let {
      // Widths are tracked per-component-index and not per-button-instance
      // The buttons were swapped inplace, but the widths were not updated yet
      assertEquals(listOf(10, 80, 10), it, "Width invariant fails")
    }

    toolbar.doLayout()
    toolbar.assertToolbarTexts("1", "3", "22222222")
    toolbar.components.map { it.width }.let {
      assertEquals(listOf(10, 10, 80), it, "Width invariant fails")
    }
  }

  private fun ActionToolbarImpl.assertToolbarTexts(vararg expected: String) {
    assertEquals(listOf(*expected), actions.map { it.templateText })
    assertEquals(listOf(*expected), components.map { (it as ActionButton).action }.map { it.templateText })
  }
}