// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.ui.representation.ideVersion

import com.intellij.ide.startup.importSettings.transfer.backend.models.IdeVersion
import com.intellij.ide.startup.importSettings.ui.representation.TransferSettingsRepresentationPanel
import com.intellij.ide.startup.importSettings.ui.representation.ideVersion.sections.TransferSettingsSection
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import java.util.EventListener
import javax.swing.JComponent
import javax.swing.JLabel

fun interface TransferSettingsIdeRepresentationListener : EventListener {
  fun action()
}

class TransferSettingsIdeRepresentation(ideVersion: IdeVersion,
                                        sectionFactory: (IdeVersion) -> List<TransferSettingsSection>,
                                        bottomComponentFactory: (() -> JComponent?)?)
  : TransferSettingsRepresentationPanel {
  private val sections = sectionFactory(ideVersion).filter { it.worthShowing() }

  private val comp = panel {
    for (section in sections) {
      row {
        cell(section.getUI()).customize(UnscaledGaps(bottom = 20))
      }
    }

    val bottomComponent = bottomComponentFactory?.invoke()
    if (bottomComponent != null) {
      if (bottomComponent.border == null) {
        bottomComponent.border = JBUI.Borders.customLineTop(JBColor.border())
      }
      row { // empty row to put bottom comp to the bottom
        cell(JLabel())
      }.resizableRow()
      row {
        panel {
          row {
            cell(bottomComponent).align(AlignX.FILL)
          }
        }.align(Align.FILL)
      }
    }
  }

  override fun block() {
    sections.forEach { it.block() }
  }

  override fun onStateChange(action: TransferSettingsIdeRepresentationListener) {
    for (section in sections) {
      section.onStateUpdate(action)
    }
  }

  override fun getComponent(): DialogPanel = comp
}