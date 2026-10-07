// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.JBColor
import com.intellij.ui.popup.PopupOwner
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JButton

private const val disclosureButtonID = "DisclosureButtonUI"


@ApiStatus.Internal
open class DisclosureButton(@NlsContexts.Button text: String? = null) : JButton(text), PopupOwner {
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

  private var textRightIconGapValue: JBValue = JBValue.UIInteger("DisclosureButton.textRightIconGap", 8)

  var textRightIconGap: Int
    get() = textRightIconGapValue.get()
    set(value) {
      if (textRightIconGap != value) {
        textRightIconGapValue = JBValue.Float(value.toFloat(), true)
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

  private var arcValue: JBValue = JBValue.UIInteger("DisclosureButton.arc", 16)

  var arc: Int
    get() = arcValue.get()
    set(value) {
      if (arc != value) {
        arcValue = JBValue.Float(value.toFloat(), true)
        revalidate()
        repaint()
      }
    }

  private var buttonHeightValue: JBValue = JBValue.Float(34f)

  var buttonHeight: Int
    get() = buttonHeightValue.get()
    set(value) {
      if (buttonHeight != value) {
        buttonHeightValue = JBValue.Float(value.toFloat(), true)
        revalidate()
        repaint()
      }
    }

  private var leftMarginValue: JBValue = JBValue.Float(14f)

  var leftMargin: Int
    get() = leftMarginValue.get()
    set(value) {
      if (leftMargin != value) {
        leftMarginValue = JBValue.Float(value.toFloat(), true)
        revalidate()
        repaint()
      }
    }

  private var rightMarginValue: JBValue = JBValue.Float(12f)

  var rightMargin: Int
    get() = rightMarginValue.get()
    set(value) {
      if (rightMargin != value) {
        rightMarginValue = JBValue.Float(value.toFloat(), true)
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

  /**
   * Replaces [arrowIcon]
   */
  var additionalAction: ActionListener? = null
    set(value) {
      if (field !== value) {
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

  override fun getBestPopupPosition(): Point {
    return Point(width + JBUI.scale(4), insets.top)
  }

  internal fun invokeAdditionalAction(e: MouseEvent) {
    additionalAction?.actionTriggered(e)
  }

  interface ActionListener {
    fun actionTriggered(e: InputEvent?)
  }
}
