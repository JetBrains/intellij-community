// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Font
import java.awt.Insets
import java.awt.geom.AffineTransform
import javax.swing.Icon

class CounterIcon(private val icon: TextIcon, initialNumber: Int) : Icon by icon {
  private var lastDigitNumber: Int = 0
  var number: Int = 0
    set(value) {
      val text = value.toString()
      if (text.length != lastDigitNumber) {
        lastDigitNumber = text.length
        val predefined = when (lastDigitNumber) {
          1 -> 0..9
          2 -> 10..99
          3 -> 100..999
          else -> null
        }
        if (predefined != null) {
          icon.setTextsForMinimumBounds(predefined.map { it.toString() })
        }
      }
      icon.text = text
    }

  init {
    number = initialNumber
  }

  constructor(initialValue: Int, foreground: Color, background: Color) :
    this(TextIcon("", foreground, background, 0), initialValue)

  companion object {
    fun createRoundIcon(number: Int, foreground: Color, background: Color): CounterIcon {
      val icon = CounterIcon(number, foreground, background)
      icon.insets = JBUI.insets(4, 6)
      icon.font = JBFont.regular()
      icon.round = icon.iconHeight
      return icon
    }
  }


  var round: Int? by icon::round
  var insets: Insets? by icon::insets
  var background: Color? by icon::background
  var foreground: Color? by icon::foreground
  var borderColor: Color? by icon::borderColor
  var withBorders: Boolean by icon::withBorders
  var font: Font? by icon::font
  var fontTransform: AffineTransform? by icon::fontTransform
}