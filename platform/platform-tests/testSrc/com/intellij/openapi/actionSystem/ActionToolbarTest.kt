// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())
    toolbar.doLayout()
    toolbar.assertToolbarTexts("1", "22222222", "3")
    toolbar.components.map { it.width }.let {  // 24 < 74 / 2
      assertTrue(it[2] < it[1] / 2, "Width invariant fails: ${it[2]} < ${it[1]} / 2")
    }

    first = false
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())
    toolbar.assertToolbarTexts("1", "3", "22222222")
    toolbar.components.map { it.width }.let {
      assertEquals(listOf(0, 0, 0), it, "Widths must not be reused")
    }
  }

  private fun ActionToolbarImpl.assertToolbarTexts(vararg expected: String) {
    assertEquals(listOf(*expected), actions.map { it.templateText })
    assertEquals(listOf(*expected), components.map { (it as ActionButton).action }.map { it.templateText })
  }
}