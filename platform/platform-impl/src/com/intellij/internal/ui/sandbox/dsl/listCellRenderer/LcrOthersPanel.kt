// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Internal
@file:Suppress("DialogTitleCapitalization")

package com.intellij.internal.ui.sandbox.dsl.listCellRenderer

import com.intellij.icons.AllIcons
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Placeholder
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

internal class LcrOthersPanel : UISandboxPanel {

  override val title: String = "Others"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group("listCellRenderer") {
        row {
          lateinit var separator: JBCheckBox
          lateinit var icon: JBCheckBox
          lateinit var selected: JBCheckBox
          lateinit var placeholder: Placeholder
          val onChanged = { _: JBCheckBox ->
            createRenderer(placeholder, separator.isSelected, icon.isSelected, selected.isSelected)
          }

          panel {
            row {
              separator = checkBox("Separator")
                .onChanged(onChanged)
                .component
            }
            row {
              icon = checkBox("Icon").selected(true)
                .onChanged(onChanged)
                .component
            }
            row {
              selected = checkBox("isSelected")
                .onChanged(onChanged)
                .component
            }
          }

          placeholder = placeholder()

          // Init
          onChanged.invoke(separator)
        }
      }

      group("Other") {
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

  private fun createRenderer(placeholder: Placeholder, separator: Boolean, icon: Boolean, selected: Boolean) {
    val debugColor = JBColor(0x60BB60, 0x60BB60)
    val renderer = listCellRenderer {
      if (separator) {
        separator { }
      }

      if (icon) {
        icon(AllIcons.General.Gear)
      }

      text(value)
      background = debugColor
    }

    placeholder.component = (renderer.getListCellRendererComponent(JBList(), "Some text", 1, selected, false) as JComponent).apply {
      isOpaque = true
      background = debugColor
    }
  }
}
