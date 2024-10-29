// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector

import com.intellij.ide.util.propComponentProperty
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import java.awt.GraphicsEnvironment

/**
 * @author Konstantin Bulenkov
 */
internal class ConfigureCustomSizeAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val centerPanel = panel {
      row("Width:") {
        intTextField(1..maxWidth())
          .bindIntText(CustomSizeModel::width)
          .columns(20)
          .focused()
      }
      row("Height:") {
        intTextField(1..maxHeight())
          .bindIntText(CustomSizeModel::height)
          .columns(20)
      }
    }

    dialog("Default Size", centerPanel, project = e.project).show()
  }

  private fun maxWidth(): Int = maxWindowBounds().width
  private fun maxHeight(): Int = maxWindowBounds().height

  private fun maxWindowBounds() = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds

  object CustomSizeModel {
    var width: Int by propComponentProperty(defaultValue = 640)
    var height: Int by propComponentProperty(defaultValue = 300)
  }
}