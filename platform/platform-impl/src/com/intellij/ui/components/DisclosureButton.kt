// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.Icon
import javax.swing.JButton

private const val disclosureButtonID = "DisclosureButtonUI"

@ApiStatus.Internal
class DisclosureButton(@NlsContexts.Button text: String? = null) : JButton(text) {

  var rightIcon: Icon? = null
    set(value) {
      if (field !== value) {
        field = value
        revalidate()
        repaint()
      }
    }

  var arrowIcon: Icon? = AllIcons.General.ChevronRight
    set(value) {
      if (field !== value) {
        field = value
        revalidate()
        repaint()
      }
    }

  var buttonBackground: Color? = null
    set(value) {
      if (field != value) {
        field = value
        revalidate()
        repaint()
      }
    }

  init {
    horizontalAlignment = LEFT
    iconTextGap = JBUIScale.scale(12)
    isRolloverEnabled = true
  }

  override fun getUIClassID(): String {
    return disclosureButtonID
  }
}