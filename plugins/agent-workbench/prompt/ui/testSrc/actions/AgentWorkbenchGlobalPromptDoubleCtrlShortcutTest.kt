// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.agent.workbench.prompt.ui.actions

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.KeyStroke

@Suppress("DEPRECATION")
private val RUN_ANYTHING_EXPLICIT_SHORTCUT = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_MASK), null)

@TestApplication
@RunInEdt(writeIntent = true)
internal class AgentWorkbenchGlobalPromptDoubleCtrlShortcutTest {
  private val component = JPanel()

  private var currentTime = 0L
  private var promptInvocationCount = 0
  private var autoSelectInvocationCount = 0
  private var runAnythingInvocationCount = 0

  private lateinit var originalPromptAction: AnAction
  private lateinit var originalAutoSelectAction: AnAction
  private lateinit var originalRunAnythingAction: AnAction
  private lateinit var originalRunAnythingShortcuts: List<Shortcut>

  @BeforeEach
  fun setUp() {
    currentTime = 0
    promptInvocationCount = 0
    autoSelectInvocationCount = 0
    runAnythingInvocationCount = 0

    val actionManager = ActionManagerEx.getInstanceEx()
    originalPromptAction = checkNotNull(actionManager.getAction(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID))
    originalAutoSelectAction = checkNotNull(actionManager.getAction(AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID))
    originalRunAnythingAction = checkNotNull(actionManager.getAction(RUN_ANYTHING_ACTION_ID))

    actionManager.asActionRuntimeRegistrar().replaceAction(
      AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID,
      createCountingAction { promptInvocationCount++ },
    )
    actionManager.asActionRuntimeRegistrar().replaceAction(
      AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID,
      createCountingAction { autoSelectInvocationCount++ },
    )
    actionManager.asActionRuntimeRegistrar().replaceAction(
      RUN_ANYTHING_ACTION_ID,
      createCountingAction { runAnythingInvocationCount++ },
    )

    originalRunAnythingShortcuts = activeKeymap().getShortcuts(RUN_ANYTHING_ACTION_ID).toList()
    resetRunAnythingShortcuts(emptyList())

    ModifierKeyDoubleClickHandler.getInstance().unsuppressAction(RUN_ANYTHING_ACTION_ID)
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID)
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID)
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(RUN_ANYTHING_ACTION_ID)
  }

  @AfterEach
  fun tearDown() {
    resetRunAnythingShortcuts(originalRunAnythingShortcuts)
    AgentWorkbenchGlobalPromptShortcutInstaller.uninstall()
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID)
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID)

    val registrar = ActionManagerEx.getInstanceEx().asActionRuntimeRegistrar()
    registrar.replaceAction(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID, originalPromptAction)
    registrar.replaceAction(AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID, originalAutoSelectAction)
    registrar.replaceAction(RUN_ANYTHING_ACTION_ID, originalRunAnythingAction)
  }

  @Test
  fun doubleCtrlInvokesAgentPrompt() {
    AgentWorkbenchGlobalPromptShortcutInstaller.install()

    dispatchDoubleCtrl()

    assertThat(promptInvocationCount).isEqualTo(1)
    assertThat(autoSelectInvocationCount).isZero()
    assertThat(runAnythingInvocationCount).isZero()
  }

  @Test
  fun altDoubleCtrlInvokesAutoSelectPrompt() {
    AgentWorkbenchGlobalPromptShortcutInstaller.install()

    dispatchAltDoubleCtrl()

    assertThat(promptInvocationCount).isZero()
    assertThat(autoSelectInvocationCount).isEqualTo(1)
    assertThat(runAnythingInvocationCount).isZero()
  }

  @Test
  fun doubleCtrlDoesNotInvokeRunAnythingWhenRunAnythingWasRegisteredFirst() {
    ModifierKeyDoubleClickHandler.getInstance().registerAction(RUN_ANYTHING_ACTION_ID, KeyEvent.VK_CONTROL, -1, false)

    AgentWorkbenchGlobalPromptShortcutInstaller.install()
    dispatchDoubleCtrl()

    assertThat(promptInvocationCount).isEqualTo(1)
    assertThat(autoSelectInvocationCount).isZero()
    assertThat(runAnythingInvocationCount).isZero()
  }

  @Test
  fun doubleCtrlDoesNotInvokeRunAnythingWhenRunAnythingIsRegisteredAfterAgentPrompt() {
    AgentWorkbenchGlobalPromptShortcutInstaller.install()
    ModifierKeyDoubleClickHandler.getInstance().registerAction(RUN_ANYTHING_ACTION_ID, KeyEvent.VK_CONTROL, -1, false)

    dispatchDoubleCtrl()

    assertThat(promptInvocationCount).isEqualTo(1)
    assertThat(autoSelectInvocationCount).isZero()
    assertThat(runAnythingInvocationCount).isZero()
  }

  @Test
  fun uninstallRestoresRunAnythingDoubleCtrlWhenRunAnythingHasNoExplicitShortcut() {
    AgentWorkbenchGlobalPromptShortcutInstaller.install()

    AgentWorkbenchGlobalPromptShortcutInstaller.uninstall()
    dispatchDoubleCtrl()

    assertThat(promptInvocationCount).isZero()
    assertThat(autoSelectInvocationCount).isZero()
    assertThat(runAnythingInvocationCount).isEqualTo(1)
  }

  @Test
  fun uninstallDoesNotRestoreRunAnythingDoubleCtrlWhenRunAnythingHasExplicitShortcut() {
    resetRunAnythingShortcuts(listOf(RUN_ANYTHING_EXPLICIT_SHORTCUT))
    AgentWorkbenchGlobalPromptShortcutInstaller.install()

    AgentWorkbenchGlobalPromptShortcutInstaller.uninstall()
    dispatchDoubleCtrl()

    assertThat(promptInvocationCount).isZero()
    assertThat(autoSelectInvocationCount).isZero()
    assertThat(runAnythingInvocationCount).isZero()
  }

  private fun resetRunAnythingShortcuts(shortcuts: List<Shortcut>) {
    val keymap = activeKeymap()
    runWriteAction {
      keymap.getShortcuts(RUN_ANYTHING_ACTION_ID).forEach { shortcut ->
        keymap.removeShortcut(RUN_ANYTHING_ACTION_ID, shortcut)
      }
      shortcuts.forEach { shortcut ->
        keymap.addShortcut(RUN_ANYTHING_ACTION_ID, shortcut)
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

  private fun dispatchCtrl(id: Int, modifiers: Int) {
    currentTime += 50
    IdeEventQueue.getInstance().dispatchEvent(
      KeyEvent(component, id, currentTime, modifiers, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED),
    )
  }

  private fun activeKeymap(): Keymap = checkNotNull(KeymapManager.getInstance()).activeKeymap

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
