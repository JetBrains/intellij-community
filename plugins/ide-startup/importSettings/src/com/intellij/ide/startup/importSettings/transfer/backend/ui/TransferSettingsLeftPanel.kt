// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.ui

import com.intellij.ide.startup.importSettings.models.BaseIdeVersion
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JList
import javax.swing.ListModel
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.event.ListSelectionEvent
import javax.swing.plaf.ComponentUI

class TransferSettingsLeftPanel(listModel: ListModel<BaseIdeVersion>) : JBScrollPane(JList(listModel)) {
  val list: JList<BaseIdeVersion> get() = (viewport.view as JList<BaseIdeVersion>)
  private var previousSelectedIndex = -1

  init {
    list.apply {
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      cellRenderer = TransferSettingsLeftPanelItemRenderer()
    }

    border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 1)
    background = UIUtil.getListBackground()
    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  }

  fun addListSelectionListener(action: JList<BaseIdeVersion>.(ListSelectionEvent) -> Unit) {
    list.addListSelectionListener s2@{
      if (list.selectedIndex == previousSelectedIndex) return@s2
      previousSelectedIndex = list.selectedIndex
      action(list, it)
    }
  }

  override fun setUI(newUI: ComponentUI?) {
    super.setUI(newUI)
  }
}