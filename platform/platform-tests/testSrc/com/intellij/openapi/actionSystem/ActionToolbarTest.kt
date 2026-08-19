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
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
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

  @Test
  @RunMethodInEdt
  fun testReplaceRightAlignedAction() {
    class A(name: String) : AnAction(name, name, null) {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }

    class B(name: String) : AnAction(name, name, null), RightAlignedToolbarAction {
      override fun actionPerformed(e: AnActionEvent) = Unit
    }

    var first = true
    val group = object : ActionGroup() {
      val actions = arrayOf(A("1"), B("4"), A("2"), A("3"))
      override fun getActionUpdateThread() = ActionUpdateThread.EDT
      override fun getChildren(e: AnActionEvent?) = if (first) actions else actions.copyOf().also { it[2] = A("22") }
    }
    val toolbar = ActionToolbarImpl("Test", group, false)
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())
    toolbar.assertToolbarComponentsTexts("1", "2", "3", "4")
    val oldButtons = toolbar.components

    first = false
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())
    toolbar.assertToolbarComponentsTexts("1", "22", "3", "4")
    val newButtons = toolbar.components

    for (i in listOf(0, 2, 3)) {
      assertSame(oldButtons[i], newButtons[i], "The button must be reused! [$i]")
    }
  }

  @Test
  @RunMethodInEdt
  fun testComponentNameFromTemplatePresentationNamesTheButton() {
    val action = NamedAction("1", "toolbar.test.first")
    val toolbar = ActionToolbarImpl("Test", MutableGroup(action), false)
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())

    assertEquals("toolbar.test.first", toolbar.buttonFor(action).name)
  }

  @Test
  @RunMethodInEdt
  fun testComponentNameIsReappliedAfterToolbarRebuild() {
    val action = NamedAction("1", "toolbar.test.first")
    val group = MutableGroup(action)
    val toolbar = ActionToolbarImpl("Test", group, false)
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())
    val firstButton = toolbar.buttonFor(action)
    assertEquals("toolbar.test.first", firstButton.name)

    // A changed child count makes `replaceButtonsForNewActionInstances` bail out, so `actionsUpdated`
    // takes the real rebuild path: `removeAll()` + `fillToolBar`, constructing brand-new buttons.
    // That is the whole point of putting the name on the action: a name stamped on the button from
    // outside would be gone from here on.
    group.children = arrayOf(action, NamedAction("2", null))
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())
    val secondButton = toolbar.buttonFor(action)

    assertNotSame(firstButton, secondButton,
                  "The toolbar must have re-created the button, otherwise this test asserts nothing")
    assertEquals("toolbar.test.first", secondButton.name)
  }

  @Test
  @RunMethodInEdt
  fun testComponentNameFallsBackToTemplateWhenCopyFromDroppedIt() {
    // `Presentation.copyFrom` reconciles the two client-property maps and drops every key the source
    // lacks, which is how a live presentation loses a name its template still declares.
    class A : AnAction("1", "1", null) {
      val donor: Presentation = Presentation("donor")

      init {
        templatePresentation.putClientProperty(ActionUtil.COMPONENT_NAME, "toolbar.test.first")
      }

      override fun actionPerformed(e: AnActionEvent) = Unit
      override fun update(e: AnActionEvent) {
        e.presentation.copyFrom(donor)
      }
    }

    val action = A()
    val toolbar = ActionToolbarImpl("Test", MutableGroup(action), false)
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())

    assertNull(toolbar.presentationFactory.getPresentation(action).getClientProperty(ActionUtil.COMPONENT_NAME),
               "The copy must have cleared the key from the live presentation, otherwise the fallback is untested")
    assertEquals("toolbar.test.first", toolbar.buttonFor(action).name,
                 "`ActionUtil.getComponentName` must fall back to the template presentation")
  }

  @Test
  @RunMethodInEdt
  fun testCustomComponentKeepsItsOwnName() {
    class A : AnAction("1", "1", null), CustomComponentAction {
      init {
        templatePresentation.putClientProperty(ActionUtil.COMPONENT_NAME, "toolbar.test.fromAction")
      }

      override fun actionPerformed(e: AnActionEvent) = Unit
      override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return JPanel().also { it.name = "toolbar.test.fromComponent" }
      }
    }

    val toolbar = ActionToolbarImpl("Test", MutableGroup(A()), false)
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())

    assertEquals("toolbar.test.fromComponent", toolbar.components.single().name,
                 "The component named itself; the toolbar must not overwrite that")
  }

  @Test
  @RunMethodInEdt
  fun testCustomComponentWithoutOwnNameGetsTheActionName() {
    class A : AnAction("1", "1", null), CustomComponentAction {
      init {
        templatePresentation.putClientProperty(ActionUtil.COMPONENT_NAME, "toolbar.test.fromAction")
      }

      override fun actionPerformed(e: AnActionEvent) = Unit
      override fun createCustomComponent(presentation: Presentation, place: String): JComponent = JPanel()
    }

    val toolbar = ActionToolbarImpl("Test", MutableGroup(A()), false)
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())

    assertEquals("toolbar.test.fromAction", toolbar.components.single().name)
  }

  @Test
  @RunMethodInEdt
  fun testNoComponentNameKeyLeavesTheButtonUnnamed() {
    val action = NamedAction("1", null)
    val toolbar = ActionToolbarImpl("Test", MutableGroup(action), false)
    PlatformTestUtil.waitForFuture(toolbar.updateActionsAsync())

    assertNull(toolbar.buttonFor(action).name, "Buttons must not be named unless the action asks for it")
  }

  private class NamedAction(text: String, componentName: String?) : AnAction(text, text, null) {
    init {
      if (componentName != null) {
        templatePresentation.putClientProperty(ActionUtil.COMPONENT_NAME, componentName)
      }
    }

    override fun actionPerformed(e: AnActionEvent) = Unit
  }

  private class MutableGroup(vararg actions: AnAction) : ActionGroup() {
    var children: Array<AnAction> = arrayOf(*actions)

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun getChildren(e: AnActionEvent?): Array<AnAction> = children
  }

  private fun ActionToolbarImpl.buttonFor(action: AnAction): ActionButton {
    return components.filterIsInstance<ActionButton>().single { it.action === action }
  }

  private fun ActionToolbarImpl.assertToolbarTexts(vararg expected: String) {
    assertToolbarActionsTexts(*expected)
    assertToolbarComponentsTexts(*expected)
  }

  private fun ActionToolbarImpl.assertToolbarActionsTexts(vararg expected: String) {
    assertEquals(listOf(*expected), actions.map { it.templateText })
  }

  private fun ActionToolbarImpl.assertToolbarComponentsTexts(vararg expected: String) {
    assertEquals(listOf(*expected), components.map { (it as ActionButton).action }.map { it.templateText })
  }
}