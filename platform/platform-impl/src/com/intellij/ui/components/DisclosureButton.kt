// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBValue
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.Icon
import javax.swing.JButton

private const val disclosureButtonID = "DisclosureButtonUI"


@ApiStatus.Internal
class DisclosureButton(@NlsContexts.Button text: String? = null) : JButton(text) {
  companion object {
    fun createSmallButton(@NlsContexts.Button text: String? = null): DisclosureButton {
      return DisclosureButton(text).apply {
        iconTextGap = JBUIScale.scale(7)
        arc = JBUIScale.scale(16)
        textRightIconGap = JBUIScale.scale(8)
        defaultBackground = null
        leftMargin = JBUIScale.scale(9)
        rightMargin = JBUIScale.scale(9)
        buttonHeight = JBUIScale.scale(28)
      }
    }
  }

  var textRightIconGap: Int = JBValue.UIInteger("DisclosureButton.textRightIconGap", 8).get()
    set(value) {
      if (field != value) {
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

  var defaultBackground: Color? = JBColor.namedColor("DisclosureButton.defaultBackground")
    set(value) {
      if (field !== value) {
        field = value
        revalidate()
        repaint()
      }
    }

  var hoverBackground: Color? = JBColor.namedColor("DisclosureButton.hoverOverlay")
    set(value) {
      if (field !== value) {
        field = value
        revalidate()
        repaint()
      }
    }

  var pressedBackground: Color? = JBColor.namedColor("DisclosureButton.pressedOverlay")
    set(value) {
      if (field !== value) {
        field = value
        revalidate()
        repaint()
      }
    }

  var arc: Int = JBValue.UIInteger("DisclosureButton.arc", 16).get()
    set(value) {
      if (field != value) {
        field = value
        revalidate()
        repaint()
      }
    }

  var buttonHeight: Int = JBUIScale.scale(34)
    set(value) {
      if (field != value) {
        field = value
        revalidate()
        repaint()
      }
    }

  var leftMargin: Int = JBUIScale.scale(14)
    set(value) {
      if (field != value) {
        field = value
        revalidate()
        repaint()
      }
    }

  var rightMargin: Int = JBUIScale.scale(12)
    set(value) {
      if (field != value) {
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
    iconTextGap = JBUIScale.scale(12)
  }

  override fun getUIClassID(): String {
    return disclosureButtonID
  }
}