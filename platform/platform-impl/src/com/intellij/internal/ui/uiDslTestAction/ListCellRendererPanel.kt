// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.iconListCellRenderer
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.JBDimension
import org.jetbrains.annotations.ApiStatus
import javax.swing.ListCellRenderer

@ApiStatus.Internal
internal class ListCellRendererPanel {

  val panel: DialogPanel = panel {
    group("JBList") {
      row {
        jbList(listOf("First", "Second", "Last"), textListCellRenderer { it })
        jbList((1..100).map { "Item $it" }, textListCellRenderer { it })
        jbList((1..100).map { "Item $it" }, iconListCellRenderer { icon, text ->
          icon.icon = if (index % 2 == 0) AllIcons.General.Add else AllIcons.General.Gear
          text.text = value
        })
      }
    }

    group("ComboBox") {
      row("Empty:") {
        comboBox(emptyList<String>(), textListCellRenderer { it })
      }
      row("No selection:") {
        comboBox(listOf("First", "Second", "Last"), textListCellRenderer { it })
          .applyToComponent { selectedItem = null }
      }
      row("Few items:") {
        comboBox(listOf("First", "Second", "Last"), textListCellRenderer { it })
      }
      row("Items with icon:") {
        comboBox((1..100).map { "Item $it" }, iconListCellRenderer { icon, text ->
          icon.icon = if (index % 2 == 0) AllIcons.General.Add else AllIcons.General.Gear
          text.text = value
        })
      }
    }
  }

  private fun <T> Row.jbList(items: List<T>, renderer: ListCellRenderer<T>) {
    val list = JBList(items)
    list.setCellRenderer(renderer)
    val scroll = JBScrollPane(list)
    scroll.minimumSize = JBDimension(100, 200)
    scroll.isOverlappingScrollBar = true
    cell(scroll)
  }
}
