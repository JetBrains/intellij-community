// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.cloneDialog

import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionStatusLine
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.IconUtil
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.GridBagLayout
import javax.accessibility.AccessibleContext
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

open class VcsCloneDialogExtensionListItem : JPanel(GridBagLayout()) {
  private val iconLabel: JLabel = JLabel()
  private val titleLabel: JLabel = JLabel()

  private val labelsPool = ArrayList<SimpleColoredComponent>()
  private val additionalLinesPanel = JPanel(VerticalLayout(0, SwingConstants.LEFT))

  init {
    border = JBUI.Borders.empty(VcsCloneDialogUiSpec.ExtensionsList.topBottomInsets, VcsCloneDialogUiSpec.ExtensionsList.leftRightInsets)
    relayout()
  }

  private fun relayout() {
    var gbc = GridBag().nextLine().next()
      .insets(JBUI.insetsRight(VcsCloneDialogUiSpec.ExtensionsList.iconTitleGap))
      .weightx(0.0)
      .anchor(GridBag.LINE_START)
      .fillCellNone()
    add(iconLabel, gbc)

    gbc = gbc.next()
      .weightx(1.0)
      .insets(JBInsets.emptyInsets())
      .fillCellHorizontally()
    titleLabel.font = JBUI.Fonts.label().asBold()
    add(titleLabel, gbc)

    gbc = gbc.nextLine().next().next()
      .insets(JBInsets.emptyInsets())
      .fillCellHorizontally()
    add(additionalLinesPanel, gbc)
  }

  fun setTitle(title: @Nls String) {
    titleLabel.text = title
  }

  fun setIcon(icon: Icon) {
    val scale = VcsCloneDialogUiSpec.ExtensionsList.iconSize.float / icon.iconWidth
    iconLabel.icon = IconUtil.scale(icon, null, scale)
  }

  fun setAdditionalStatusLines(additionalLines: List<VcsCloneDialogExtensionStatusLine>) {
    additionalLinesPanel.removeAll()
    while (labelsPool.size < additionalLines.size) {
      labelsPool.add(SimpleColoredComponent())
    }

    for ((index, line) in additionalLines.withIndex()) {
      val component = labelsPool[index]
      component.ipad = JBInsets.emptyInsets()
      component.clear()
      component.append(line.text, line.attribute, line.actionListener)
      additionalLinesPanel.add(component)
    }
  }

  fun setTitleForeground(foreground: Color) {
    titleLabel.foreground = foreground
  }

  override fun getAccessibleContext(): AccessibleContext {
    return titleLabel.accessibleContext
  }
}