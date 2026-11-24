// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.picker

import com.intellij.openapi.actionSystem.MouseShortcut
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JPanel

/** Shortcut matching and internal-mode gating of the global pick gesture (no IDE needed). */
class PickGestureMatcherTest {
  private val source = JPanel()
  private val ctrlAltShift = InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK
  private val gesture = listOf(MouseShortcut(MouseEvent.BUTTON1, ctrlAltShift, 1))

  private fun event(id: Int, modifiersEx: Int, button: Int = MouseEvent.BUTTON1, clickCount: Int = 1): MouseEvent {
    val buttonDown = if (id == MouseEvent.MOUSE_PRESSED && button == MouseEvent.BUTTON1) InputEvent.BUTTON1_DOWN_MASK else 0
    return MouseEvent(source, id, 0L, modifiersEx or buttonDown, 5, 5, clickCount, false, button)
  }

  @Test
  fun `press of the gesture picks, release and click are consumed`() {
    assertEquals(GestureMatch.PICK, PickGestureMatcher.classify(event(MouseEvent.MOUSE_PRESSED, ctrlAltShift), gesture, true))
    assertEquals(GestureMatch.CONSUME, PickGestureMatcher.classify(event(MouseEvent.MOUSE_RELEASED, ctrlAltShift), gesture, true))
    assertEquals(GestureMatch.CONSUME, PickGestureMatcher.classify(event(MouseEvent.MOUSE_CLICKED, ctrlAltShift), gesture, true))
  }

  @Test
  fun `does not clash with the UI Inspector's Ctrl+Alt+Click`() {
    val uiInspector = InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK
    assertEquals(GestureMatch.NONE, PickGestureMatcher.classify(event(MouseEvent.MOUSE_PRESSED, uiInspector), gesture, true))
  }

  @Test
  fun `other buttons, modifiers and event kinds are ignored`() {
    assertEquals(GestureMatch.NONE, PickGestureMatcher.classify(event(MouseEvent.MOUSE_PRESSED, 0), gesture, true))
    assertEquals(GestureMatch.NONE, PickGestureMatcher.classify(event(MouseEvent.MOUSE_PRESSED, ctrlAltShift, MouseEvent.BUTTON3), gesture, true))
    assertEquals(GestureMatch.NONE, PickGestureMatcher.classify(event(MouseEvent.MOUSE_MOVED, ctrlAltShift, MouseEvent.NOBUTTON, 0), gesture, true))
    assertEquals(GestureMatch.NONE, PickGestureMatcher.classify(event(MouseEvent.MOUSE_DRAGGED, ctrlAltShift, MouseEvent.BUTTON1, 0), gesture, true))
    val withMeta = ctrlAltShift or InputEvent.META_DOWN_MASK
    assertEquals(GestureMatch.NONE, PickGestureMatcher.classify(event(MouseEvent.MOUSE_PRESSED, withMeta), gesture, true))
  }

  @Test
  fun `a quick second gesture (click count 2) still picks`() {
    assertEquals(GestureMatch.PICK, PickGestureMatcher.classify(event(MouseEvent.MOUSE_PRESSED, ctrlAltShift, clickCount = 2), gesture, true))
  }

  @Test
  fun `inactive outside internal mode or without a keymap shortcut`() {
    assertEquals(GestureMatch.NONE, PickGestureMatcher.classify(event(MouseEvent.MOUSE_PRESSED, ctrlAltShift), gesture, false))
    assertEquals(GestureMatch.NONE, PickGestureMatcher.classify(event(MouseEvent.MOUSE_PRESSED, ctrlAltShift), emptyList(), true))
  }

  @Test
  fun `rebound shortcut from the keymap is honored`() {
    val metaAlt = InputEvent.META_DOWN_MASK or InputEvent.ALT_DOWN_MASK
    val rebound = listOf(MouseShortcut(MouseEvent.BUTTON1, metaAlt, 1))
    assertEquals(GestureMatch.PICK, PickGestureMatcher.classify(event(MouseEvent.MOUSE_PRESSED, metaAlt), rebound, true))
    assertEquals(GestureMatch.NONE, PickGestureMatcher.classify(event(MouseEvent.MOUSE_PRESSED, ctrlAltShift), rebound, true))
  }

  @Test
  fun `plain Swing components are not excluded`() {
    assertFalse(PickGestureMatcher.isExcludedTarget(JPanel()))
    assertFalse(PickGestureMatcher.isExcludedTarget(null))
  }
}
