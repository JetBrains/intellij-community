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
enum class DisclosureButtonKind {
  Default, Klein
}

@ApiStatus.Internal
class DisclosureButton(@NlsContexts.Button text: String? = null) : JButton(text) {
  var kind: DisclosureButtonKind = DisclosureButtonKind.Default
    set(value) {
      if (field !== value) {
        field = value
        revalidate()
        repaint()
      }
    }

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
    isRolloverEnabled = true
  }

  override fun getIconTextGap(): Int {
    return if (kind == DisclosureButtonKind.Klein) {
      JBUIScale.scale(7)
    }
    else {
      JBUIScale.scale(12)
    }
  }

  override fun getUIClassID(): String {
    return disclosureButtonID
  }
}