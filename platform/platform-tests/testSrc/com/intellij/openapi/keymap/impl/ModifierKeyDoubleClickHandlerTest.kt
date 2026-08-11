// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardGestureAction
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.Clock
import com.intellij.testFramework.common.waitUntilAssertSucceedsBlocking
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private const val MY_SHIFT_SHIFT_ACTION = "ModifierKeyDoubleClickHandlerTest.action1"
private const val MY_SHIFT_KEY_ACTION = "ModifierKeyDoubleClickHandlerTest.action2"
private const val MY_SHIFT_SHIFT_KEY_ACTION = "ModifierKeyDoubleClickHandlerTest.action3"
private const val MY_SHIFT_OTHER_KEY_ACTION = "ModifierKeyDoubleClickHandlerTest.action4"
private const val MY_CTRL_CTRL_WITH_ALT_ACTION = "ModifierKeyDoubleClickHandlerTest.action5"
private const val MY_SUPPRESSED_META_META_ACTION = "ModifierKeyDoubleClickHandlerTest.action6"
private const val MY_KEYMAP_CTRL_CTRL_ACTION = "ModifierKeyDoubleClickHandlerTest.action7"
private const val MY_KEYMAP_CTRL_CTRL_OVERRIDE_ACTION = "ModifierKeyDoubleClickHandlerTest.action8"
private const val MY_KEYMAP_HOLD_CTRL_ACTION = "ModifierKeyDoubleClickHandlerTest.action9"
private const val MY_COMPARATOR_SNAPSHOT_FIRST_ACTION = "ModifierKeyDoubleClickHandlerTest.snapshot.z"
private const val MY_COMPARATOR_SNAPSHOT_SECOND_ACTION = "ModifierKeyDoubleClickHandlerTest.snapshot.a"
private const val RUN_ANYTHING_ACTION = "RunAnything"

@Suppress("DEPRECATION")
private val SHIFT_KEY_SHORTCUT = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, InputEvent.SHIFT_MASK), null)

@Suppress("DEPRECATION")
private val SHIFT_OTHER_KEY_SHORTCUT = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK), null)

@Suppress("DEPRECATION")
private val CTRL_KEY_SHORTCUT = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_MASK), null)

@Suppress("DEPRECATION")
private val CTRL_CTRL_SHORTCUT = KeyboardModifierGestureShortcut.newInstance(
  KeyboardGestureAction.ModifierType.dblClick,
  KeyStroke.getKeyStroke(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK),
) as KeyboardModifierGestureShortcut

@Suppress("DEPRECATION")
private val ALT_GRAPH_CTRL_CTRL_SHORTCUT = KeyboardModifierGestureShortcut.newInstance(
  KeyboardGestureAction.ModifierType.dblClick,
  KeyStroke.getKeyStroke(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK or InputEvent.ALT_GRAPH_MASK),
) as KeyboardModifierGestureShortcut

@Suppress("DEPRECATION")
private val HOLD_CTRL_SHORTCUT = KeyboardModifierGestureShortcut.newInstance(
  KeyboardGestureAction.ModifierType.hold,
  KeyStroke.getKeyStroke(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK),
) as KeyboardModifierGestureShortcut

private fun createAction(isEnabled: () -> Boolean = { true }, onPerformed: () -> Unit): AnAction {
  return object : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.setEnabledAndVisible(isEnabled())
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
  private var ctrlCtrlWithAltActionInvocationCount = 0
  private var suppressedMetaMetaActionInvocationCount = 0
  private var keymapCtrlCtrlActionInvocationCount = 0
  private var keymapCtrlCtrlOverrideActionInvocationCount = 0
  private var keymapHoldCtrlActionInvocationCount = 0
  private var keymapCtrlCtrlActionEnabled = true
  private var keymapCtrlCtrlOverrideActionEnabled = true
  private val originalGestureShortcuts = linkedMapOf<String, List<KeyboardModifierGestureShortcut>>()

  @BeforeEach
  fun setUp() {
    UIUtil.dispatchAllInvocationEvents()
    clearKeymapShortcuts()
    val doubleClickHandler = ModifierKeyDoubleClickHandler.getInstance()
    doubleClickHandler.suppressAction(IdeActions.ACTION_SEARCH_EVERYWHERE)
    doubleClickHandler.suppressAction(RUN_ANYTHING_ACTION)
    removeActiveKeymapGestureShortcuts()
    Clock.setTime(0)
    keymapCtrlCtrlActionEnabled = true
    keymapCtrlCtrlOverrideActionEnabled = true

    val actionManager = ActionManager.getInstance()
    actionManager.registerAction(MY_SHIFT_SHIFT_ACTION, createAction { shiftShiftActionInvocationCount++ })
    actionManager.registerAction(MY_SHIFT_KEY_ACTION, createAction { shiftKeyActionInvocationCount++ })
    actionManager.registerAction(MY_SHIFT_SHIFT_KEY_ACTION, createAction { shiftShiftKeyActionInvocationCount++ })
    actionManager.registerAction(MY_SHIFT_OTHER_KEY_ACTION, createAction { shiftOtherKeyActionInvocationCount++ })
    actionManager.registerAction(MY_CTRL_CTRL_WITH_ALT_ACTION, createAction { ctrlCtrlWithAltActionInvocationCount++ })
    actionManager.registerAction(MY_SUPPRESSED_META_META_ACTION, createAction { suppressedMetaMetaActionInvocationCount++ })
    actionManager.registerAction(MY_KEYMAP_CTRL_CTRL_ACTION, createAction(
      isEnabled = { keymapCtrlCtrlActionEnabled },
    ) { keymapCtrlCtrlActionInvocationCount++ })
    actionManager.registerAction(MY_KEYMAP_CTRL_CTRL_OVERRIDE_ACTION, createAction(
      isEnabled = { keymapCtrlCtrlOverrideActionEnabled },
    ) { keymapCtrlCtrlOverrideActionInvocationCount++ })
    actionManager.registerAction(MY_KEYMAP_HOLD_CTRL_ACTION, createAction { keymapHoldCtrlActionInvocationCount++ })

    val activeKeymap = KeymapManager.getInstance().activeKeymap
    activeKeymap.addShortcut(MY_SHIFT_KEY_ACTION, SHIFT_KEY_SHORTCUT)
    activeKeymap.addShortcut(MY_SHIFT_OTHER_KEY_ACTION, SHIFT_OTHER_KEY_SHORTCUT)
    UIUtil.dispatchAllInvocationEvents()
    clearKeymapShortcuts()

    doubleClickHandler.registerAction(MY_SHIFT_SHIFT_ACTION, KeyEvent.VK_SHIFT, -1)
    doubleClickHandler.registerAction(MY_SHIFT_SHIFT_KEY_ACTION, KeyEvent.VK_SHIFT, KeyEvent.VK_BACK_SPACE)
    doubleClickHandler.registerAction(MY_CTRL_CTRL_WITH_ALT_ACTION, KeyEvent.VK_CONTROL, -1, KeyEvent.VK_ALT, false)
  }

  @AfterEach
  fun tearDown() {
    val doubleClickHandler = ModifierKeyDoubleClickHandler.getInstance()
    doubleClickHandler.unsuppressAction(MY_SUPPRESSED_META_META_ACTION)
    doubleClickHandler.unregisterAction(MY_SUPPRESSED_META_META_ACTION)
    doubleClickHandler.unregisterAction(MY_CTRL_CTRL_WITH_ALT_ACTION)
    doubleClickHandler.unregisterAction(MY_SHIFT_SHIFT_KEY_ACTION)
    doubleClickHandler.unregisterAction(MY_SHIFT_SHIFT_ACTION)

    val activeKeymap = KeymapManager.getInstance().activeKeymap
    resetActionShortcuts(MY_KEYMAP_HOLD_CTRL_ACTION, emptyList())
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_OVERRIDE_ACTION, emptyList())
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_ACTION, emptyList())
    activeKeymap.removeShortcut(MY_SHIFT_OTHER_KEY_ACTION, SHIFT_OTHER_KEY_SHORTCUT)
    activeKeymap.removeShortcut(MY_SHIFT_KEY_ACTION, SHIFT_KEY_SHORTCUT)
    restoreActiveKeymapGestureShortcuts()
    UIUtil.dispatchAllInvocationEvents()
    clearKeymapShortcuts()
    doubleClickHandler.unsuppressAction(RUN_ANYTHING_ACTION)
    doubleClickHandler.unsuppressAction(IdeActions.ACTION_SEARCH_EVERYWHERE)

    val actionManager = ActionManager.getInstance()
    actionManager.unregisterAction(MY_KEYMAP_HOLD_CTRL_ACTION)
    actionManager.unregisterAction(MY_KEYMAP_CTRL_CTRL_OVERRIDE_ACTION)
    actionManager.unregisterAction(MY_KEYMAP_CTRL_CTRL_ACTION)
    actionManager.unregisterAction(MY_COMPARATOR_SNAPSHOT_SECOND_ACTION)
    actionManager.unregisterAction(MY_COMPARATOR_SNAPSHOT_FIRST_ACTION)
    actionManager.unregisterAction(MY_SUPPRESSED_META_META_ACTION)
    actionManager.unregisterAction(MY_CTRL_CTRL_WITH_ALT_ACTION)
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

  @Test
  fun ctrlCtrlWithRequiredAltSuccessfulCase() {
    ctrlPressWithAlt()
    ctrlReleaseWithAlt()
    ctrlPressWithAlt()
    assertEquals(0, ctrlCtrlWithAltActionInvocationCount)

    ctrlReleaseWithAlt()

    assertEquals(1, ctrlCtrlWithAltActionInvocationCount)
  }

  @Test
  fun ctrlCtrlWithRequiredAltAllowsPhysicalAltKeyEvent() {
    altPress()
    ctrlPressWithAlt()
    ctrlReleaseWithAlt()
    ctrlPressWithAlt()
    ctrlReleaseWithAlt()
    altRelease()

    assertEquals(1, ctrlCtrlWithAltActionInvocationCount)
  }

  @Test
  fun ctrlCtrlWithRequiredAltDoesNotTriggerWithoutAlt() {
    ctrlPress()
    ctrlRelease()
    ctrlPress()
    ctrlRelease()

    assertEquals(0, ctrlCtrlWithAltActionInvocationCount)
  }

  @Test
  fun keymapGestureShortcutRegistersRuntimeDoubleClick() {
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_ACTION, listOf(CTRL_CTRL_SHORTCUT))

    syncKeymapShortcuts()

    assertTrue(ModifierKeyDoubleClickHandler.getInstance().isShortcutRegistered(MY_KEYMAP_CTRL_CTRL_ACTION, CTRL_CTRL_SHORTCUT))
    dispatchCtrlDoubleClick()
    assertEquals(1, keymapCtrlCtrlActionInvocationCount)
  }

  @Test
  fun keymapGestureShortcutListenerResyncsRuntimeDoubleClick() {
    val doubleClickHandler = ModifierKeyDoubleClickHandler.getInstance()

    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_ACTION, listOf(CTRL_CTRL_SHORTCUT))
    waitUntilAssertSucceedsBlocking(timeout = 2.seconds) {
      assertTrue(doubleClickHandler.isShortcutRegistered(MY_KEYMAP_CTRL_CTRL_ACTION, CTRL_CTRL_SHORTCUT))
    }
    dispatchCtrlDoubleClick()
    assertEquals(1, keymapCtrlCtrlActionInvocationCount)

    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_ACTION, emptyList())
    waitUntilAssertSucceedsBlocking(timeout = 2.seconds) {
      assertFalse(doubleClickHandler.isShortcutRegistered(MY_KEYMAP_CTRL_CTRL_ACTION, CTRL_CTRL_SHORTCUT))
    }
    dispatchCtrlDoubleClick()
    assertEquals(1, keymapCtrlCtrlActionInvocationCount)
  }

  @Test
  fun keymapGestureShortcutSurvivesRuntimeResyncDuringDoubleClick() {
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_ACTION, listOf(CTRL_CTRL_SHORTCUT))
    syncKeymapShortcuts()

    assertTrue(ModifierKeyDoubleClickHandler.getInstance().isShortcutRegistered(MY_KEYMAP_CTRL_CTRL_ACTION, CTRL_CTRL_SHORTCUT))
    ctrlPress()
    ctrlRelease()
    syncKeymapShortcuts()
    ctrlPress()
    ctrlRelease()
    assertEquals(1, keymapCtrlCtrlActionInvocationCount)
  }

  @Test
  fun removingKeymapGestureShortcutUnregistersRuntimeDoubleClick() {
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_ACTION, listOf(CTRL_CTRL_SHORTCUT))
    syncKeymapShortcuts()
    assertTrue(ModifierKeyDoubleClickHandler.getInstance().isShortcutRegistered(MY_KEYMAP_CTRL_CTRL_ACTION, CTRL_CTRL_SHORTCUT))

    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_ACTION, emptyList())
    syncKeymapShortcuts()

    assertFalse(ModifierKeyDoubleClickHandler.getInstance().isShortcutRegistered(MY_KEYMAP_CTRL_CTRL_ACTION, CTRL_CTRL_SHORTCUT))
    dispatchCtrlDoubleClick()
    assertEquals(0, keymapCtrlCtrlActionInvocationCount)
  }

  @Test
  fun bareDoubleClickSkippedBecauseActionHasShortcutDoesNotConsumeEvent() {
    val doubleClickHandler = ModifierKeyDoubleClickHandler.getInstance()
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_ACTION, listOf(CTRL_KEY_SHORTCUT))
    doubleClickHandler.registerAction(MY_KEYMAP_CTRL_CTRL_ACTION, KeyEvent.VK_CONTROL, -1, true)

    try {
      ctrlPress()
      ctrlRelease()
      ctrlPress()
      val finalRelease = ctrlRelease()

      assertEquals(0, keymapCtrlCtrlActionInvocationCount)
      assertFalse(finalRelease.isConsumed)
    }
    finally {
      doubleClickHandler.unregisterAction(MY_KEYMAP_CTRL_CTRL_ACTION)
    }
  }

  @Test
  fun duplicateKeymapGestureShortcutRegistersAllActions() {
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_ACTION, listOf(CTRL_CTRL_SHORTCUT))
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_OVERRIDE_ACTION, listOf(CTRL_CTRL_SHORTCUT))

    syncKeymapShortcuts()

    assertTrue(ModifierKeyDoubleClickHandler.getInstance().isShortcutRegistered(MY_KEYMAP_CTRL_CTRL_ACTION, CTRL_CTRL_SHORTCUT))
    assertTrue(ModifierKeyDoubleClickHandler.getInstance().isShortcutRegistered(MY_KEYMAP_CTRL_CTRL_OVERRIDE_ACTION, CTRL_CTRL_SHORTCUT))
  }

  @Test
  fun duplicateKeymapGestureShortcutInvokesFirstEnabledAction() {
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_ACTION, listOf(CTRL_CTRL_SHORTCUT))
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_OVERRIDE_ACTION, listOf(CTRL_CTRL_SHORTCUT))

    syncKeymapShortcuts()

    dispatchCtrlDoubleClick()
    assertEquals(1, keymapCtrlCtrlActionInvocationCount)
    assertEquals(0, keymapCtrlCtrlOverrideActionInvocationCount)
  }

  @Test
  fun duplicateKeymapGestureShortcutInvokesNextEnabledAction() {
    keymapCtrlCtrlActionEnabled = false
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_ACTION, listOf(CTRL_CTRL_SHORTCUT))
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_OVERRIDE_ACTION, listOf(CTRL_CTRL_SHORTCUT))

    syncKeymapShortcuts()

    dispatchCtrlDoubleClick()
    assertEquals(0, keymapCtrlCtrlActionInvocationCount)
    assertEquals(1, keymapCtrlCtrlOverrideActionInvocationCount)
  }

  @Test
  fun keymapHoldGestureShortcutIsIgnoredByDoubleClickHandler() {
    resetActionShortcuts(MY_KEYMAP_HOLD_CTRL_ACTION, listOf(HOLD_CTRL_SHORTCUT))

    syncKeymapShortcuts()

    assertFalse(ModifierKeyDoubleClickHandler.getInstance().isShortcutRegistered(MY_KEYMAP_HOLD_CTRL_ACTION, HOLD_CTRL_SHORTCUT))
    dispatchCtrlDoubleClick()
    assertEquals(0, keymapHoldCtrlActionInvocationCount)
  }

  @Test
  fun suppressedKeymapGestureShortcutIsNotRegistered() {
    val doubleClickHandler = ModifierKeyDoubleClickHandler.getInstance()
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_ACTION, listOf(CTRL_CTRL_SHORTCUT))
    doubleClickHandler.suppressShortcut(MY_KEYMAP_CTRL_CTRL_ACTION, CTRL_CTRL_SHORTCUT)

    syncKeymapShortcuts()

    assertFalse(doubleClickHandler.isShortcutRegistered(MY_KEYMAP_CTRL_CTRL_ACTION, CTRL_CTRL_SHORTCUT))
    dispatchCtrlDoubleClick()
    assertEquals(0, keymapCtrlCtrlActionInvocationCount)
    doubleClickHandler.unsuppressAction(MY_KEYMAP_CTRL_CTRL_ACTION)
  }

  @Test
  fun unregisteringActionResyncsKeymapGestureShortcuts() {
    val actionManager = ActionManager.getInstance()
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_ACTION, listOf(CTRL_CTRL_SHORTCUT))
    syncKeymapShortcuts()
    assertTrue(ModifierKeyDoubleClickHandler.getInstance().isShortcutRegistered(MY_KEYMAP_CTRL_CTRL_ACTION, CTRL_CTRL_SHORTCUT))

    actionManager.unregisterAction(MY_KEYMAP_CTRL_CTRL_ACTION)
    waitUntilAssertSucceedsBlocking(timeout = 2.seconds) {
      assertFalse(ModifierKeyDoubleClickHandler.getInstance().isShortcutRegistered(MY_KEYMAP_CTRL_CTRL_ACTION, CTRL_CTRL_SHORTCUT))
    }

    dispatchCtrlDoubleClick()
    assertEquals(0, keymapCtrlCtrlActionInvocationCount)

    actionManager.registerAction(MY_KEYMAP_CTRL_CTRL_ACTION, createAction { keymapCtrlCtrlActionInvocationCount++ })
    waitUntilAssertSucceedsBlocking(timeout = 2.seconds) {
      assertTrue(ModifierKeyDoubleClickHandler.getInstance().isShortcutRegistered(MY_KEYMAP_CTRL_CTRL_ACTION, CTRL_CTRL_SHORTCUT))
    }

    dispatchCtrlDoubleClick()
    assertEquals(1, keymapCtrlCtrlActionInvocationCount)
  }

  @Test
  fun keymapGestureShortcutWithUnsupportedModifierIsIgnored() {
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_ACTION, listOf(ALT_GRAPH_CTRL_CTRL_SHORTCUT))

    syncKeymapShortcuts()

    assertFalse(ModifierKeyDoubleClickHandler.getInstance().isShortcutRegistered(MY_KEYMAP_CTRL_CTRL_ACTION, ALT_GRAPH_CTRL_CTRL_SHORTCUT))
    dispatchCtrlDoubleClick()
    assertEquals(0, keymapCtrlCtrlActionInvocationCount)
  }

  @Test
  fun registrationOrderComparatorUsesStableSnapshot() {
    val actionManager = ActionManagerEx.getInstanceEx()
    val comparator = actionManager.registrationOrderComparator
    val comparisonBeforeRegistration = comparator.compare(MY_COMPARATOR_SNAPSHOT_FIRST_ACTION, MY_COMPARATOR_SNAPSHOT_SECOND_ACTION)

    actionManager.registerAction(MY_COMPARATOR_SNAPSHOT_FIRST_ACTION, createAction {})
    actionManager.registerAction(MY_COMPARATOR_SNAPSHOT_SECOND_ACTION, createAction {})

    assertEquals(
      comparisonBeforeRegistration,
      comparator.compare(MY_COMPARATOR_SNAPSHOT_FIRST_ACTION, MY_COMPARATOR_SNAPSHOT_SECOND_ACTION),
    )
  }

  @Test
  fun unsupportedModifierDoesNotTriggerRegisteredDoubleClick() {
    resetActionShortcuts(MY_KEYMAP_CTRL_CTRL_ACTION, listOf(CTRL_CTRL_SHORTCUT))
    syncKeymapShortcuts()

    dispatchCtrlDoubleClickWithUnsupportedButtonModifier()

    assertEquals(0, keymapCtrlCtrlActionInvocationCount)
  }

  @Test
  fun suppressActionUnregistersAction() {
    val doubleClickHandler = ModifierKeyDoubleClickHandler.getInstance()
    doubleClickHandler.registerAction(MY_SUPPRESSED_META_META_ACTION, KeyEvent.VK_META, -1, false)
    assertTrue(doubleClickHandler.isActionRegistered(MY_SUPPRESSED_META_META_ACTION))

    doubleClickHandler.suppressAction(MY_SUPPRESSED_META_META_ACTION)

    assertFalse(doubleClickHandler.isActionRegistered(MY_SUPPRESSED_META_META_ACTION))
    metaPress()
    metaRelease()
    metaPress()
    metaRelease()
    assertEquals(0, suppressedMetaMetaActionInvocationCount)
  }

  @Test
  fun suppressedActionIgnoresRegistrationUntilUnsuppressed() {
    val doubleClickHandler = ModifierKeyDoubleClickHandler.getInstance()
    doubleClickHandler.suppressAction(MY_SUPPRESSED_META_META_ACTION)

    doubleClickHandler.registerAction(MY_SUPPRESSED_META_META_ACTION, KeyEvent.VK_META, -1, false)

    assertFalse(doubleClickHandler.isActionRegistered(MY_SUPPRESSED_META_META_ACTION))
    metaPress()
    metaRelease()
    metaPress()
    metaRelease()
    assertEquals(0, suppressedMetaMetaActionInvocationCount)

    doubleClickHandler.unsuppressAction(MY_SUPPRESSED_META_META_ACTION)
    doubleClickHandler.registerAction(MY_SUPPRESSED_META_META_ACTION, KeyEvent.VK_META, -1, false)

    assertTrue(doubleClickHandler.isActionRegistered(MY_SUPPRESSED_META_META_ACTION))
    metaPress()
    metaRelease()
    metaPress()
    metaRelease()
    assertEquals(1, suppressedMetaMetaActionInvocationCount)
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

  private fun altPress() {
    dispatchEvent(KeyEvent.KEY_PRESSED, InputEvent.ALT_MASK, KeyEvent.VK_ALT, KeyEvent.CHAR_UNDEFINED)
  }

  private fun altRelease() {
    dispatchEvent(KeyEvent.KEY_RELEASED, 0, KeyEvent.VK_ALT, KeyEvent.CHAR_UNDEFINED)
  }

  private fun metaPress() {
    dispatchEvent(KeyEvent.KEY_PRESSED, InputEvent.META_MASK, KeyEvent.VK_META, KeyEvent.CHAR_UNDEFINED)
  }

  private fun metaRelease() {
    dispatchEvent(KeyEvent.KEY_RELEASED, 0, KeyEvent.VK_META, KeyEvent.CHAR_UNDEFINED)
  }

  private fun ctrlPress() {
    dispatchEvent(KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED)
  }

  private fun ctrlRelease(): KeyEvent {
    return dispatchEvent(KeyEvent.KEY_RELEASED, 0, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED)
  }

  private fun dispatchCtrlDoubleClick() {
    ctrlPress()
    ctrlRelease()
    ctrlPress()
    ctrlRelease()
  }

  private fun dispatchCtrlDoubleClickWithUnsupportedButtonModifier() {
    ctrlPressWithUnsupportedButtonModifier()
    ctrlReleaseWithUnsupportedButtonModifier()
    ctrlPressWithUnsupportedButtonModifier()
    ctrlReleaseWithUnsupportedButtonModifier()
  }

  private fun ctrlPressWithAlt() {
    dispatchEvent(KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK or InputEvent.ALT_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED)
  }

  private fun ctrlReleaseWithAlt() {
    dispatchEvent(KeyEvent.KEY_RELEASED, InputEvent.ALT_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED)
  }

  private fun ctrlPressWithUnsupportedButtonModifier() {
    dispatchEvent(KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK or InputEvent.BUTTON1_DOWN_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED)
  }

  private fun ctrlReleaseWithUnsupportedButtonModifier() {
    dispatchEvent(KeyEvent.KEY_RELEASED, InputEvent.BUTTON1_DOWN_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED)
  }

  private fun dispatchEvent(id: Int, modifiers: Int, keyCode: Int, keyChar: Char): KeyEvent {
    val event = KeyEvent(component, id, Clock.getTime(), modifiers, keyCode, keyChar)
    IdeEventQueue.getInstance().dispatchEvent(event)
    return event
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

  private fun resetActionShortcuts(actionId: String, shortcuts: List<Shortcut>) {
    val activeKeymap = KeymapManager.getInstance().activeKeymap
    runWriteAction {
      activeKeymap.getShortcuts(actionId).forEach { shortcut ->
        activeKeymap.removeShortcut(actionId, shortcut)
      }
      shortcuts.forEach { shortcut ->
        activeKeymap.addShortcut(actionId, shortcut)
      }
    }
  }

  private fun removeActiveKeymapGestureShortcuts() {
    val activeKeymap = KeymapManager.getInstance().activeKeymap
    originalGestureShortcuts.clear()
    for (actionId in activeKeymap.actionIdList) {
      val gestureShortcuts = activeKeymap.getShortcuts(actionId).filterIsInstance<KeyboardModifierGestureShortcut>()
      if (gestureShortcuts.isNotEmpty()) {
        originalGestureShortcuts[actionId] = gestureShortcuts
      }
    }

    runWriteAction {
      originalGestureShortcuts.forEach { (actionId, shortcuts) ->
        shortcuts.forEach { shortcut -> activeKeymap.removeShortcut(actionId, shortcut) }
      }
    }
  }

  private fun restoreActiveKeymapGestureShortcuts() {
    if (originalGestureShortcuts.isEmpty()) {
      return
    }

    val activeKeymap = KeymapManager.getInstance().activeKeymap
    runWriteAction {
      originalGestureShortcuts.forEach { (actionId, shortcuts) ->
        shortcuts.forEach { shortcut -> activeKeymap.addShortcut(actionId, shortcut) }
      }
    }
    originalGestureShortcuts.clear()
  }

  private fun syncKeymapShortcuts() {
    val method = ModifierKeyDoubleClickHandler::class.java.getDeclaredMethod("syncKeymapShortcuts")
    method.isAccessible = true
    method.invoke(ModifierKeyDoubleClickHandler.getInstance())
  }

  private fun clearKeymapShortcuts() {
    val method = ModifierKeyDoubleClickHandler::class.java.getDeclaredMethod("clearKeymapShortcuts")
    method.isAccessible = true
    method.invoke(ModifierKeyDoubleClickHandler.getInstance())
  }
}
