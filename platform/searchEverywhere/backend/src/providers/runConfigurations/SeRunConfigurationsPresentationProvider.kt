// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.runConfigurations

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ChooseRunConfigurationPopup
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.platform.searchEverywhere.presentations.SeBasicItemPresentationBuilder
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import org.jetbrains.annotations.ApiStatus
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

@ApiStatus.Internal
object SeRunConfigurationsPresentationProvider {
  /**
   * Returns the presentation of [item]. The description names the executor that [command] selects.
   * Without a command, the description names the executors of Enter and Shift+Enter.
   */
  suspend fun getPresentation(item: ChooseRunConfigurationPopup.ItemWrapper<*>, command: SeRunConfigurationsCommand?): SeItemPresentation {
    val description = readAction {
      if (command == null) getShortcutsDescription()
      else (item.value as? RunnerAndConfigurationSettings)?.let { command.findExecutor(it) }?.actionName
    }

    return SeBasicItemPresentationBuilder()
      .withIcon(item.icon)
      .withText(item.text)
      .withDescription(description)
      .build()
  }

  private fun getShortcutsDescription(): String {
    val debugExecutor = ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG)
    val runExecutor: Executor? = DefaultRunExecutor.getRunExecutorInstance()
    val enterStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
    val shiftEnterStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)

    val descriptionText = StringBuilder()
    if (debugExecutor != null) {
      descriptionText.append(debugExecutor.getActionName())
      descriptionText.append("(" + KeymapUtil.getKeystrokeText(enterStroke) + ")")
      if (runExecutor != null) {
        descriptionText.append(" / " + runExecutor.getActionName())
        descriptionText.append("(" + KeymapUtil.getKeystrokeText(shiftEnterStroke) + ")")
      }
    }
    else {
      if (runExecutor != null) {
        descriptionText.append(runExecutor.getActionName())
        descriptionText.append("(" + KeymapUtil.getKeystrokeText(enterStroke) + ")")
      }
    }
    return descriptionText.toString()
  }
}
