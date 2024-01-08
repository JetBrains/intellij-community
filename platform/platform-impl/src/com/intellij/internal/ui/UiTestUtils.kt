// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.ListSelectionModel

internal fun createListSelectionPanel(data: Map<String, JComponent>, proportionKey: String): JComponent {
  val result = JBSplitter(false, proportionKey, 0.2f)
  val listData = data.keys.sorted()
  val list = JBList(data.keys.sorted()).apply {
    setCellRenderer(textListCellRenderer { it })
    addListSelectionListener {
      val component = data[listData.getOrNull(selectedIndex)]
      if (component == null) {
        result.secondComponent = null
      }
      else {
        component.border = JBUI.Borders.empty(10)
        result.secondComponent = JBScrollPane(component)
      }
    }
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    selectedIndex = 0
  }
  result.apply {
    firstComponent = JBScrollPane(list)
    minimumSize = JBDimension(300, 200)
  }
  return result
}