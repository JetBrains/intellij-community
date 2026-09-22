// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.tests.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTabbedPane
import javax.swing.SwingConstants

internal class JTabbedPaneTestPanel : UISandboxPanel {

  override val title: String = "JTabbedPane"

  private val tabPlacements = mapOf(
    SwingConstants.TOP to DevkitUiDslBundle.message("sandbox.tabbed.pane.tab.placement.top.text"),
    SwingConstants.LEFT to DevkitUiDslBundle.message("sandbox.tabbed.pane.tab.placement.left.text"),
    SwingConstants.BOTTOM to DevkitUiDslBundle.message("sandbox.tabbed.pane.tab.placement.bottom.text"),
    SwingConstants.RIGHT to DevkitUiDslBundle.message("sandbox.tabbed.pane.tab.placement.right.text"))

  override fun createContent(disposable: Disposable): JComponent {
    val tabbedPane = JTabbedPane(SwingConstants.TOP, JTabbedPane.SCROLL_TAB_LAYOUT)
    for (i in 1..21) {
      val label = JLabel(DevkitUiDslBundle.message("sandbox.tabbed.pane.label.text", i), SwingConstants.CENTER)
      val icon = if (i == 19) AllIcons.FileTypes.Any_type else null
      tabbedPane.addTab(DevkitUiDslBundle.message("sandbox.tabbed.pane.tab.text", i), icon, label)
    }

    return panel {
      row(DevkitUiDslBundle.message("sandbox.tabbed.pane.tab.placement.text")) {
        comboBox(tabPlacements.keys, textListCellRenderer("") {
          tabPlacements[it]
        }).applyToComponent {
          selectedItem = tabbedPane.tabPlacement
        }.onChanged {
          tabbedPane.tabPlacement = it.selectedItem as Int
        }

        checkBox(DevkitUiDslBundle.message("sandbox.tabbed.pane.tab.multiline.text"))
          .selected(tabbedPane.tabLayoutPolicy == JTabbedPane.WRAP_TAB_LAYOUT)
          .onChanged { tabbedPane.tabLayoutPolicy = if (it.isSelected) JTabbedPane.WRAP_TAB_LAYOUT else JTabbedPane.SCROLL_TAB_LAYOUT }
      }

      row {
        cell(tabbedPane)
          .align(Align.FILL)
      }.resizableRow()
    }
  }
}
