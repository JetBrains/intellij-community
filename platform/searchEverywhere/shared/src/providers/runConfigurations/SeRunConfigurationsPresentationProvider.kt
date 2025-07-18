// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.runConfigurations

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.actions.ChooseRunConfigurationPopup
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.platform.searchEverywhere.SeItemPresentation
import com.intellij.platform.searchEverywhere.SeSimpleItemPresentation
import org.jetbrains.annotations.ApiStatus
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

@ApiStatus.Internal
object SeRunConfigurationsPresentationProvider {
  fun getPresentation(item: ChooseRunConfigurationPopup.ItemWrapper<*>, extendedDescription: String?, isMultiSelectionSupported: Boolean): SeItemPresentation {
    val debugExecutor = ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG)
    val runExecutor = DefaultRunExecutor.getRunExecutorInstance()
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

    return SeSimpleItemPresentation(iconId = item.icon?.rpcId(),
                                    text = item.text,
                                    description = descriptionText.toString(),
                                    extendedDescription = extendedDescription,
                                    isMultiSelectionSupported = isMultiSelectionSupported)
  }
}