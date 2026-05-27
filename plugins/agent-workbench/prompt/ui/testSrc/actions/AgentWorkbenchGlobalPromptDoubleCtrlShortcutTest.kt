// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.agent.workbench.prompt.ui.actions

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardGestureAction
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler
import com.intellij.ide.actions.runAnything.RunAnythingAction
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.KeyStroke

private val AGENT_PROMPT_DOUBLE_CTRL_SHORTCUT = KeyboardModifierGestureShortcut.newInstance(
  KeyboardGestureAction.ModifierType.dblClick,
  KeyStroke.getKeyStroke(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK),
)
private val AGENT_PROMPT_ALT_DOUBLE_CTRL_SHORTCUT = KeyboardModifierGestureShortcut.newInstance(
  KeyboardGestureAction.ModifierType.dblClick,
  KeyStroke.getKeyStroke(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK or InputEvent.ALT_MASK),
)
private val AGENT_PROMPT_ALT_SHIFT_DOUBLE_CTRL_SHORTCUT = KeyboardModifierGestureShortcut.newInstance(
  KeyboardGestureAction.ModifierType.dblClick,
  KeyStroke.getKeyStroke(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK or InputEvent.ALT_MASK or InputEvent.SHIFT_MASK),
)
private val RUN_ANYTHING_SHIFT_DOUBLE_CTRL_SHORTCUT = KeyboardModifierGestureShortcut.newInstance(
  KeyboardGestureAction.ModifierType.dblClick,
  KeyStroke.getKeyStroke(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK or InputEvent.SHIFT_MASK),
)

@TestApplication
@RunInEdt(writeIntent = true)
@Timeout(value = 2, unit = TimeUnit.MINUTES)
internal class AgentWorkbenchGlobalPromptDoubleCtrlShortcutTest {
  private val component = JPanel()

  private var currentTime = 0L
  private var promptInvocationCount = 0
  private var autoSelectInvocationCount = 0
  private var runAnythingInvocationCount = 0

  private var originalPromptAction: AnAction? = null
  private var originalAutoSelectAction: AnAction? = null
  private var originalRunAnythingAction: AnAction? = null
  private var originalPromptShortcuts: List<Shortcut> = emptyList()
  private var originalAutoSelectShortcuts: List<Shortcut> = emptyList()
  private var originalRunAnythingShortcuts: List<Shortcut> = emptyList()
  private val actionsRegisteredByTest = mutableSetOf<String>()

  @BeforeEach
  fun setUp() {
    currentTime = 0
    promptInvocationCount = 0
    autoSelectInvocationCount = 0
    runAnythingInvocationCount = 0

    val actionManager = ActionManagerEx.getInstanceEx()
    ensureActionRegistered(actionManager, RunAnythingAction.RUN_ANYTHING_ACTION_ID)
    ensureActionRegistered(actionManager, AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID)
    ensureActionRegistered(actionManager, AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID)

    originalPromptAction = checkNotNull(actionManager.getAction(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID))
    originalAutoSelectAction = checkNotNull(actionManager.getAction(AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID))
    originalRunAnythingAction = checkNotNull(actionManager.getAction(RunAnythingAction.RUN_ANYTHING_ACTION_ID))

    actionManager.asActionRuntimeRegistrar().replaceAction(
      AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID,
      createCountingAction { promptInvocationCount++ },
    )
    actionManager.asActionRuntimeRegistrar().replaceAction(
      AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID,
      createCountingAction { autoSelectInvocationCount++ },
    )
    actionManager.asActionRuntimeRegistrar().replaceAction(
      RunAnythingAction.RUN_ANYTHING_ACTION_ID,
      createCountingAction { runAnythingInvocationCount++ },
    )

    originalPromptShortcuts = activeKeymap().getShortcuts(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID).toList()
    originalAutoSelectShortcuts = activeKeymap().getShortcuts(AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID).toList()
    originalRunAnythingShortcuts = activeKeymap().getShortcuts(RunAnythingAction.RUN_ANYTHING_ACTION_ID).toList()
    resetActionShortcuts(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID, listOf(AGENT_PROMPT_DOUBLE_CTRL_SHORTCUT))
    resetActionShortcuts(AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID, listOf(AGENT_PROMPT_ALT_DOUBLE_CTRL_SHORTCUT))
    resetActionShortcuts(RunAnythingAction.RUN_ANYTHING_ACTION_ID, listOf(AGENT_PROMPT_DOUBLE_CTRL_SHORTCUT))

    ModifierKeyDoubleClickHandler.getInstance().unsuppressAction(RunAnythingAction.RUN_ANYTHING_ACTION_ID)
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID)
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID)
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(RunAnythingAction.RUN_ANYTHING_ACTION_ID)
  }

  @AfterEach
  fun tearDown() {
    resetActionShortcuts(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID, originalPromptShortcuts)
    resetActionShortcuts(AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID, originalAutoSelectShortcuts)
    resetActionShortcuts(RunAnythingAction.RUN_ANYTHING_ACTION_ID, originalRunAnythingShortcuts)
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID)
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID)
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(RunAnythingAction.RUN_ANYTHING_ACTION_ID)

    val registrar = ActionManagerEx.getInstanceEx().asActionRuntimeRegistrar()
    originalPromptAction?.let { registrar.replaceAction(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID, it) }
    originalAutoSelectAction?.let { registrar.replaceAction(AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID, it) }
    originalRunAnythingAction?.let { registrar.replaceAction(RunAnythingAction.RUN_ANYTHING_ACTION_ID, it) }
    actionsRegisteredByTest.forEach(ActionManagerEx.getInstanceEx()::unregisterAction)
    actionsRegisteredByTest.clear()
    syncKeymapShortcuts()
  }

  @Test
  fun doubleCtrlInvokesAgentPrompt() {
    syncKeymapShortcuts()

    dispatchDoubleCtrl()

    assertThat(promptInvocationCount).isEqualTo(1)
    assertThat(autoSelectInvocationCount).isZero()
    assertThat(runAnythingInvocationCount).isZero()
  }

  @Test
  fun altDoubleCtrlInvokesAutoSelectPrompt() {
    syncKeymapShortcuts()

    dispatchAltDoubleCtrl()

    assertThat(promptInvocationCount).isZero()
    assertThat(autoSelectInvocationCount).isEqualTo(1)
    assertThat(runAnythingInvocationCount).isZero()
  }

  @Test
  fun multiplePromptDoubleClickShortcutsInvokeAgentPrompt() {
    resetActionShortcuts(
      AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID,
      listOf(AGENT_PROMPT_DOUBLE_CTRL_SHORTCUT, AGENT_PROMPT_ALT_DOUBLE_CTRL_SHORTCUT),
    )
    resetActionShortcuts(AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID, emptyList())
    syncKeymapShortcuts()

    dispatchDoubleCtrl()
    dispatchAltDoubleCtrl()

    assertThat(promptInvocationCount).isEqualTo(2)
    assertThat(autoSelectInvocationCount).isZero()
    assertThat(runAnythingInvocationCount).isZero()
  }

  @Test
  fun multiModifierDoubleCtrlInvokesAgentPrompt() {
    resetActionShortcuts(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID, listOf(AGENT_PROMPT_ALT_SHIFT_DOUBLE_CTRL_SHORTCUT))
    resetActionShortcuts(AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID, emptyList())
    resetActionShortcuts(RunAnythingAction.RUN_ANYTHING_ACTION_ID, emptyList())
    syncKeymapShortcuts()

    dispatchAltShiftDoubleCtrl()

    assertThat(promptInvocationCount).isEqualTo(1)
    assertThat(autoSelectInvocationCount).isZero()
    assertThat(runAnythingInvocationCount).isZero()
  }

  @Test
  fun runAnythingNonBareDoubleClickSurvivesAgentBareDoubleCtrlDisplacement() {
    resetActionShortcuts(
      RunAnythingAction.RUN_ANYTHING_ACTION_ID,
      listOf(AGENT_PROMPT_DOUBLE_CTRL_SHORTCUT, RUN_ANYTHING_SHIFT_DOUBLE_CTRL_SHORTCUT),
    )
    syncKeymapShortcuts()

    dispatchShiftDoubleCtrl()
    dispatchDoubleCtrl()

    assertThat(promptInvocationCount).isEqualTo(1)
    assertThat(autoSelectInvocationCount).isZero()
    assertThat(runAnythingInvocationCount).isEqualTo(1)
  }

  @Test
  fun doubleCtrlShortcutRemovalDisablesAgentPromptAndRestoresRunAnything() {
    syncKeymapShortcuts()
    resetActionShortcuts(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID, emptyList())
    syncKeymapShortcuts()
    dispatchDoubleCtrl()

    assertThat(promptInvocationCount).isZero()
    assertThat(autoSelectInvocationCount).isZero()
    assertThat(runAnythingInvocationCount).isEqualTo(1)
  }

  @Test
  fun altDoubleCtrlShortcutRemovalDisablesAutoSelectPrompt() {
    syncKeymapShortcuts()
    resetActionShortcuts(AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID, emptyList())
    syncKeymapShortcuts()
    dispatchAltDoubleCtrl()

    assertThat(promptInvocationCount).isZero()
    assertThat(autoSelectInvocationCount).isZero()
    assertThat(runAnythingInvocationCount).isZero()
  }

  @Test
  fun removingAgentPromptAndRunAnythingShortcutsDoesNotLeaveHiddenDoubleCtrl() {
    syncKeymapShortcuts()
    resetActionShortcuts(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID, emptyList())
    resetActionShortcuts(RunAnythingAction.RUN_ANYTHING_ACTION_ID, emptyList())
    syncKeymapShortcuts()
    dispatchDoubleCtrl()

    assertThat(promptInvocationCount).isZero()
    assertThat(autoSelectInvocationCount).isZero()
    assertThat(runAnythingInvocationCount).isZero()
  }

  private fun resetActionShortcuts(actionId: String, shortcuts: List<Shortcut>) {
    val keymap = activeKeymap()
    runWriteAction {
      keymap.getShortcuts(actionId).forEach { shortcut ->
        keymap.removeShortcut(actionId, shortcut)
      }
      shortcuts.forEach { shortcut ->
        keymap.addShortcut(actionId, shortcut)
      }
    }
  }

  private fun dispatchDoubleCtrl() {
    dispatchCtrl(KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK)
    dispatchCtrl(KeyEvent.KEY_RELEASED, 0)
    dispatchCtrl(KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK)
    dispatchCtrl(KeyEvent.KEY_RELEASED, 0)
  }

  private fun dispatchAltDoubleCtrl() {
    dispatchCtrl(KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK or InputEvent.ALT_MASK)
    dispatchCtrl(KeyEvent.KEY_RELEASED, InputEvent.ALT_MASK)
    dispatchCtrl(KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK or InputEvent.ALT_MASK)
    dispatchCtrl(KeyEvent.KEY_RELEASED, InputEvent.ALT_MASK)
  }

  private fun dispatchShiftDoubleCtrl() {
    dispatchCtrl(KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK or InputEvent.SHIFT_MASK)
    dispatchCtrl(KeyEvent.KEY_RELEASED, InputEvent.SHIFT_MASK)
    dispatchCtrl(KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK or InputEvent.SHIFT_MASK)
    dispatchCtrl(KeyEvent.KEY_RELEASED, InputEvent.SHIFT_MASK)
  }

  private fun dispatchAltShiftDoubleCtrl() {
    dispatchCtrl(KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK or InputEvent.ALT_MASK or InputEvent.SHIFT_MASK)
    dispatchCtrl(KeyEvent.KEY_RELEASED, InputEvent.ALT_MASK or InputEvent.SHIFT_MASK)
    dispatchCtrl(KeyEvent.KEY_PRESSED, InputEvent.CTRL_MASK or InputEvent.ALT_MASK or InputEvent.SHIFT_MASK)
    dispatchCtrl(KeyEvent.KEY_RELEASED, InputEvent.ALT_MASK or InputEvent.SHIFT_MASK)
  }

  private fun dispatchCtrl(id: Int, modifiers: Int) {
    currentTime += 50
    IdeEventQueue.getInstance().dispatchEvent(
      KeyEvent(component, id, currentTime, modifiers, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED),
    )
  }

  private fun activeKeymap(): Keymap = checkNotNull(KeymapManager.getInstance()).activeKeymap

  private fun ensureActionRegistered(actionManager: ActionManagerEx, actionId: String) {
    if (actionManager.getAction(actionId) != null) {
      return
    }

    actionManager.registerAction(actionId, createCountingAction {})
    actionsRegisteredByTest.add(actionId)
  }

  private fun syncKeymapShortcuts() {
    val method = ModifierKeyDoubleClickHandler::class.java.getDeclaredMethod("syncKeymapShortcuts")
    method.isAccessible = true
    method.invoke(ModifierKeyDoubleClickHandler.getInstance())
  }

  private fun createCountingAction(onPerformed: () -> Unit): AnAction {
    return object : AnAction() {
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
      }

      override fun actionPerformed(e: AnActionEvent) {
        onPerformed()
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }
  }
}
