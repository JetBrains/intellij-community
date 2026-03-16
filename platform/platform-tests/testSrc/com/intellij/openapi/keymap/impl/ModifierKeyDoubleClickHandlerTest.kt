// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.Clock
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.UIUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import kotlin.test.assertEquals

private const val MY_SHIFT_SHIFT_ACTION = "ModifierKeyDoubleClickHandlerTest.action1"
private const val MY_SHIFT_KEY_ACTION = "ModifierKeyDoubleClickHandlerTest.action2"
private const val MY_SHIFT_SHIFT_KEY_ACTION = "ModifierKeyDoubleClickHandlerTest.action3"
private const val MY_SHIFT_OTHER_KEY_ACTION = "ModifierKeyDoubleClickHandlerTest.action4"

@Suppress("DEPRECATION")
private val SHIFT_KEY_SHORTCUT = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, InputEvent.SHIFT_MASK), null)
@Suppress("DEPRECATION")
private val SHIFT_OTHER_KEY_SHORTCUT = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK), null)

private fun createAction(onPerformed: () -> Unit): AnAction {
  return object : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.setEnabledAndVisible(true)
    }

    override fun actionPerformed(e: AnActionEvent) {
      onPerformed()
    }
  }
}

@RunInEdt(writeIntent = true)
@TestApplication
@Suppress("DEPRECATION")
class ModifierKeyDoubleClickHandlerTest {
  private val component: JComponent = JPanel()

  private var currentTime: Long = 0
  private var shiftShiftActionInvocationCount = 0
  private var shiftKeyActionInvocationCount = 0
  private var shiftShiftKeyActionInvocationCount = 0
  private var shiftOtherKeyActionInvocationCount = 0

  @BeforeEach
  fun setUp() {
    Clock.setTime(0)

    val actionManager = ActionManager.getInstance()
    actionManager.registerAction(MY_SHIFT_SHIFT_ACTION, createAction { shiftShiftActionInvocationCount++ })
    actionManager.registerAction(MY_SHIFT_KEY_ACTION, createAction { shiftKeyActionInvocationCount++ })
    actionManager.registerAction(MY_SHIFT_SHIFT_KEY_ACTION, createAction { shiftShiftKeyActionInvocationCount++ })
    actionManager.registerAction(MY_SHIFT_OTHER_KEY_ACTION, createAction { shiftOtherKeyActionInvocationCount++ })

    val activeKeymap = KeymapManager.getInstance().activeKeymap
    activeKeymap.addShortcut(MY_SHIFT_KEY_ACTION, SHIFT_KEY_SHORTCUT)
    activeKeymap.addShortcut(MY_SHIFT_OTHER_KEY_ACTION, SHIFT_OTHER_KEY_SHORTCUT)

    val doubleClickHandler = ModifierKeyDoubleClickHandler.getInstance()
    doubleClickHandler.registerAction(MY_SHIFT_SHIFT_ACTION, KeyEvent.VK_SHIFT, -1)
    doubleClickHandler.registerAction(MY_SHIFT_SHIFT_KEY_ACTION, KeyEvent.VK_SHIFT, KeyEvent.VK_BACK_SPACE)
  }

  @AfterEach
  fun tearDown() {
    val doubleClickHandler = ModifierKeyDoubleClickHandler.getInstance()
    doubleClickHandler.unregisterAction(MY_SHIFT_SHIFT_KEY_ACTION)
    doubleClickHandler.unregisterAction(MY_SHIFT_SHIFT_ACTION)

    val activeKeymap = KeymapManager.getInstance().activeKeymap
    activeKeymap.removeShortcut(MY_SHIFT_OTHER_KEY_ACTION, SHIFT_OTHER_KEY_SHORTCUT)
    activeKeymap.removeShortcut(MY_SHIFT_KEY_ACTION, SHIFT_KEY_SHORTCUT)

    val actionManager = ActionManager.getInstance()
    actionManager.unregisterAction(MY_SHIFT_OTHER_KEY_ACTION)
    actionManager.unregisterAction(MY_SHIFT_SHIFT_KEY_ACTION)
    actionManager.unregisterAction(MY_SHIFT_KEY_ACTION)
    actionManager.unregisterAction(MY_SHIFT_SHIFT_ACTION)

    Clock.reset()
  }

  @Test
  fun shiftShiftSuccessfulCase() {
    press()
    release()
    press()
    assertInvocationCounts(0, 0, 0)
    release()
    assertInvocationCounts(0, 1, 0)
  }

  @Test
  fun longSecondClick() {
    press()
    release()
    press()
    currentTime += 400
    Clock.setTime(currentTime)
    release()
    assertInvocationCounts(0, 0, 0)
  }

  @Test
  fun shiftShiftKeySuccessfulCase() {
    press()
    release()
    press()
    key()
    assertInvocationCounts(0, 0, 1)
    release()
    assertInvocationCounts(0, 0, 1)
  }

  @Test
  fun shiftKey() {
    press()
    key()
    assertInvocationCounts(1, 0, 0)
    release()
  }

  @Test
  fun repeatedInvocationOnKeyHold() {
    press()
    release()
    press()
    key(2)
    assertInvocationCounts(0, 0, 2)
    release()
    assertInvocationCounts(0, 0, 2)
  }

  @Test
  fun noTriggeringAfterUnrelatedAction() {
    press()
    release()
    press()
    otherKey()
    UIUtil.dispatchAllInvocationEvents()
    key()
    release()
    assertEquals(0, shiftShiftActionInvocationCount)
    assertEquals(0, shiftShiftKeyActionInvocationCount)
    assertEquals(1, shiftOtherKeyActionInvocationCount)
  }

  @Test
  fun shiftShiftOtherModifierNoAction() {
    press()
    release()
    press()
    dispatchEvent(KeyEvent.KEY_PRESSED, InputEvent.SHIFT_MASK or InputEvent.CTRL_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED)

    dispatchEvent(KeyEvent.KEY_PRESSED, InputEvent.SHIFT_MASK or InputEvent.CTRL_MASK, KeyEvent.VK_BACK_SPACE, '\b')
    dispatchEvent(KeyEvent.KEY_TYPED, InputEvent.SHIFT_MASK or InputEvent.CTRL_MASK, 0, '\b')
    dispatchEvent(KeyEvent.KEY_RELEASED, InputEvent.SHIFT_MASK or InputEvent.CTRL_MASK, KeyEvent.VK_BACK_SPACE, '\b')

    dispatchEvent(KeyEvent.KEY_RELEASED, InputEvent.SHIFT_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED)
    release()
    assertInvocationCounts(0, 0, 0)
  }

  private fun assertInvocationCounts(shiftKeyCount: Int, shiftShiftCount: Int, shiftShiftKeyCount: Int) {
    assertEquals(shiftKeyCount, shiftKeyActionInvocationCount)
    assertEquals(shiftShiftCount, shiftShiftActionInvocationCount)
    assertEquals(shiftShiftKeyCount, shiftShiftKeyActionInvocationCount)
    assertEquals(0, shiftOtherKeyActionInvocationCount)
  }

  private fun press() {
    dispatchEvent(KeyEvent.KEY_PRESSED, InputEvent.SHIFT_MASK, KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED)
  }

  private fun release() {
    dispatchEvent(KeyEvent.KEY_RELEASED, 0, KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED)
  }

  private fun dispatchEvent(id: Int, modifiers: Int, keyCode: Int, keyChar: Char) {
    IdeEventQueue.getInstance().dispatchEvent(
      KeyEvent(component, id, Clock.getTime(), modifiers, keyCode, keyChar),
    )
  }

  private fun otherKey() {
    key(otherKey = true)
  }

  private fun key(repeat: Int = 1, otherKey: Boolean = false) {
    val keyCode = if (otherKey) KeyEvent.VK_ENTER else KeyEvent.VK_BACK_SPACE
    val keyChar = if (otherKey) '\n' else '\b'

    repeat(repeat) {
      dispatchEvent(KeyEvent.KEY_PRESSED, InputEvent.SHIFT_MASK, keyCode, keyChar)
      dispatchEvent(KeyEvent.KEY_TYPED, InputEvent.SHIFT_MASK, 0, keyChar)
    }

    dispatchEvent(KeyEvent.KEY_RELEASED, InputEvent.SHIFT_MASK, keyCode, keyChar)
  }
}
