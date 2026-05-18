// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.actionSystem.impl.DynamicActionConfigurationCustomizer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.messages.MessageBusConnection
import java.awt.event.KeyEvent

internal const val AGENT_WORKBENCH_PROMPT_DOUBLE_CTRL_REGISTRY_KEY: String = "agent.workbench.prompt.double.ctrl.enabled"

internal const val AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID: String = "AgentWorkbenchPrompt.OpenGlobalPalette"
internal const val AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID: String = "AgentWorkbenchPrompt.OpenGlobalPaletteAutoSelect"
internal const val RUN_ANYTHING_ACTION_ID: String = "RunAnything"

internal class AgentWorkbenchGlobalPromptShortcutCustomizer : DynamicActionConfigurationCustomizer,
                                                               ActionConfigurationCustomizer.LightCustomizeStrategy {
  override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
    if (actionRegistrar.getActionOrStub(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID) == null) {
      return
    }
    if (!RegistryManager.getInstanceAsync().get(AGENT_WORKBENCH_PROMPT_DOUBLE_CTRL_REGISTRY_KEY).asBoolean()) {
      return
    }
    AgentWorkbenchGlobalPromptShortcutInstaller.installFromActionConfiguration()
  }

  override fun unregisterActions(actionManager: ActionManager) {
    AgentWorkbenchGlobalPromptShortcutInstaller.uninstall()
  }
}

internal object AgentWorkbenchGlobalPromptShortcutInstaller {
  private var installed = false
  private var keymapConnection: MessageBusConnection? = null

  @Synchronized
  fun install() {
    doInstall()
  }

  @Synchronized
  fun installFromActionConfiguration() {
    doInstall()
  }

  private fun doInstall() {
    installed = true
    displaceRunAnythingDoubleCtrl()
    val doubleClickHandler = ModifierKeyDoubleClickHandler.getInstance()
    doubleClickHandler.registerAction(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID, KeyEvent.VK_CONTROL, -1, false)
    doubleClickHandler.registerAction(
      AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID,
      KeyEvent.VK_CONTROL,
      -1,
      KeyEvent.VK_ALT,
      false,
    )
    subscribeToKeymapChangesIfNeeded()
  }

  @Synchronized
  fun uninstall() {
    installed = false
    keymapConnection?.disconnect()
    keymapConnection = null

    val doubleClickHandler = ModifierKeyDoubleClickHandler.getInstance()
    doubleClickHandler.unregisterAction(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID)
    doubleClickHandler.unregisterAction(AGENT_WORKBENCH_GLOBAL_PROMPT_AUTO_SELECT_ACTION_ID)
    restoreRunAnythingDoubleCtrlIfNeeded()
  }

  private fun subscribeToKeymapChangesIfNeeded() {
    if (keymapConnection != null) {
      return
    }

    keymapConnection = ApplicationManager.getApplication().messageBus.connect().also { connection ->
      connection.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
        override fun activeKeymapChanged(keymap: Keymap?) {
          reinstallRunAnythingDisplacement()
        }

        override fun shortcutsChanged(keymap: Keymap, actionIds: Collection<String>, fromSettings: Boolean) {
          if (RUN_ANYTHING_ACTION_ID in actionIds) {
            reinstallRunAnythingDisplacement()
          }
        }
      })
    }
  }

  @Synchronized
  private fun reinstallRunAnythingDisplacement() {
    if (installed) {
      displaceRunAnythingDoubleCtrl()
    }
  }

  private fun displaceRunAnythingDoubleCtrl() {
    ModifierKeyDoubleClickHandler.getInstance().suppressAction(RUN_ANYTHING_ACTION_ID)
  }

  private fun restoreRunAnythingDoubleCtrlIfNeeded() {
    ModifierKeyDoubleClickHandler.getInstance().unsuppressAction(RUN_ANYTHING_ACTION_ID)
    val activeKeymap = KeymapManager.getInstance()?.activeKeymap ?: return
    if (activeKeymap.getShortcuts(RUN_ANYTHING_ACTION_ID).isEmpty()) {
      ModifierKeyDoubleClickHandler.getInstance().registerAction(RUN_ANYTHING_ACTION_ID, KeyEvent.VK_CONTROL, -1, false)
    }
  }
}
