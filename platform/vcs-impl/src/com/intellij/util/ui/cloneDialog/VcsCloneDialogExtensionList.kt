// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.cloneDialog

import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtension
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SingleSelectionModel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.border.EmptyBorder

class VcsCloneDialogExtensionList(listModel: ListModel<VcsCloneDialogExtension>) : JBList<VcsCloneDialogExtension>(listModel) {
  init {
    selectionModel = SingleSelectionModel()
    cellRenderer = Renderer()

    ScrollingUtil.installActions(this)
  }

  class Renderer : ListCellRenderer<VcsCloneDialogExtension> {
    val component = VcsCloneDialogExtensionListItem
    val wrapper: JComponent

    init {
      wrapper = JPanel(FlowLayout(FlowLayout.LEADING)).apply {
        border = EmptyBorder(UIUtil.PANEL_REGULAR_INSETS)
        add(component)
      }
    }

    override fun getListCellRendererComponent(list: JList<out VcsCloneDialogExtension>,
                                              value: VcsCloneDialogExtension,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      component.setTitle(value.getName())
      component.setTitleForeground(ListUiUtil.WithTallRow.foreground(list, isSelected))
      component.setIcon(value.getIcon())
      UIUtil.setBackgroundRecursively(wrapper, ListUiUtil.WithTallRow.background(list, isSelected))
      return wrapper
    }
  }
}

