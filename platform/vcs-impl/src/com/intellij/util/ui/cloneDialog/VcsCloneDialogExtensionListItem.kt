// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.cloneDialog

import com.intellij.util.IconUtil
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.GridBagLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel


object VcsCloneDialogExtensionListItem : JComponent() {

  private val iconLabel: JLabel = JLabel()
  private val titleLabel: JLabel = JLabel()

  init {
    layout = GridBagLayout()

    var gbc = GridBag().next()
      .insetRight(UIUtil.DEFAULT_VGAP)
      .anchor(GridBag.LINE_START)
      .fillCellNone()
    add(iconLabel, gbc)

    gbc = gbc.next()
      .insets(JBUI.emptyInsets())
      .anchor(GridBag.BELOW_BASELINE_LEADING)
      .fillCellHorizontally()
    titleLabel.font = JBUI.Fonts.label().asBold()
    add(titleLabel, gbc)
  }

  fun setTitle(title: String) {
    titleLabel.text = title
  }

  fun setIcon(icon: Icon) {
    iconLabel.icon = IconUtil.scale(icon, null, 22.0f/icon.iconHeight) // scale is chosen so that the size of icon corresponds the design
  }

  fun setTitleForeground(foreground: Color) {
    titleLabel.foreground = foreground
  }
}