// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.cloneDialog

import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SingleSelectionModel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.ListModel

class VcsCloneDialogExtensionList(listModel: ListModel<VcsCloneDialogExtension>) : JBList<VcsCloneDialogExtension>(listModel) {
  init {
    selectionModel = SingleSelectionModel()
    val renderer = Renderer()
    cellRenderer = renderer

    UIUtil.putClientProperty(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(renderer))
    ScrollingUtil.installActions(this)
  }

  class Renderer
    : ListCellRenderer<VcsCloneDialogExtension>,
      VcsCloneDialogExtensionListItem() {
    override fun getListCellRendererComponent(list: JList<out VcsCloneDialogExtension>,
                                              extension: VcsCloneDialogExtension,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      setTitle(extension.getName())
      setTitleForeground(ListUiUtil.WithTallRow.foreground(isSelected, true))
      setIcon(extension.getIcon())
      toolTipText = extension.getTooltip()
      setAdditionalStatusLines(extension.getAdditionalStatusLines())
      UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, isSelected, true))
      return this
    }
  }
}

