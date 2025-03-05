// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.UIManager
import javax.swing.border.EmptyBorder


@Deprecated("Use Kotlin UI DSL 2, for description use {@link com.intellij.ui.dsl.builder.Cell#comment}", level = DeprecationLevel.HIDDEN)
class CheckBoxWithDescription(val checkBox: JCheckBox, description: String?) : JPanel() {
  init {
    layout = BorderLayout()
    add(checkBox, BorderLayout.NORTH)

    if (description != null) {
      val iconSize = checkBox.preferredSize.height
      val desc = DescriptionLabel(description)
      desc.border = EmptyBorder(0, iconSize + UIManager.getInt("CheckBox.textIconGap"), 0, 0)
      add(desc, BorderLayout.CENTER)
    }
  }
}
