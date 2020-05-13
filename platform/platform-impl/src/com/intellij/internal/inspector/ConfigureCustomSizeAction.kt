// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.inspector

import com.intellij.ide.util.propComponentProperty
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import java.awt.GraphicsEnvironment

/**
 * @author Konstantin Bulenkov
 */
class ConfigureCustomSizeAction: DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val centerPanel = panel {
      row("Width:") { intTextField(CustomSizeModel::width, 20, 1..maxWidth()).focused() }
      row("Height:") { intTextField(CustomSizeModel::height, 20, 1..maxHeight()) }
    }

    dialog("Default Size", centerPanel, project = e.project).show()
  }

  private fun maxWidth(): Int = maxWindowBounds().width
  private fun maxHeight(): Int = maxWindowBounds().height

  private fun maxWindowBounds() = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds

  object CustomSizeModel {
    var width by propComponentProperty(defaultValue = 640)
    var height by propComponentProperty(defaultValue = 300)
  }
}