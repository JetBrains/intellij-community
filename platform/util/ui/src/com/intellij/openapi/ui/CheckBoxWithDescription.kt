// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager
import javax.swing.border.EmptyBorder


@Deprecated("Use Kotlin UI DSL 2, for description use {@link com.intellij.ui.dsl.builder.Cell#comment}", level = DeprecationLevel.HIDDEN)
@ApiStatus.ScheduledForRemoval
class CheckBoxWithDescription(val checkBox: JCheckBox, description: String?) : JPanel() {
  init {
    layout = BorderLayout()
    add(checkBox, BorderLayout.NORTH)

    if (description != null) {
      val iconSize = checkBox.preferredSize.height
      val desc = DescriptionLabelCopy(description)
      desc.border = EmptyBorder(0, iconSize + UIManager.getInt("CheckBox.textIconGap"), 0, 0)
      add(desc, BorderLayout.CENTER)
    }
  }
}

private class DescriptionLabelCopy(text: String?) : JLabel() {
  init {
    setText(text)
  }

  override fun updateUI() {
    super.updateUI()
    setForeground(UIUtil.getLabelDisabledForeground())
    var size = getFont().getSize()
    if (size >= 12) {
      size -= 2
    }
    setFont(getFont().deriveFont(size.toFloat()))
  }
}