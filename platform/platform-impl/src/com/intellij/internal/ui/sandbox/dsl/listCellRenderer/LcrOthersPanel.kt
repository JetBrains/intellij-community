// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Internal

package com.intellij.internal.ui.sandbox.dsl.listCellRenderer

import com.intellij.icons.AllIcons
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

internal class LcrOthersPanel : UISandboxPanel {

  override val title: String = "Others"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group("Renderers") {
        row("iconListCellRenderer:") {
          val renderer = listCellRenderer {
            icon(AllIcons.General.Gear)
            text(value)
          }
          cell(renderer.getListCellRendererComponent(JBList(), "Some text", 0, false, false) as JComponent)
        }

        row("SimpleListCellRenderer:") {
          val renderer = SimpleListCellRenderer.create<String> { label, value, _ ->
            label.icon = AllIcons.General.Gear
            label.text = value
          }
          cell(renderer.getListCellRendererComponent(JBList(), "Some text", 0, false, false) as JComponent)
        }
      }
    }
  }
}
