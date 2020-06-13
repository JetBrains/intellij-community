// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import sun.swing.SwingUtilities2
import java.awt.FontMetrics
import java.io.File
import javax.swing.JComponent

open class ClippingTitle(prefix: String = " - ", suffix: String = "") : DefaultPartTitle(prefix, suffix), ShrinkingTitlePart {
  companion object {
    const val ellipsisSymbol = "\u2026"

    fun clipString(component: JComponent, string: String, maxWidth: Int, fileSeparatorChar: String = File.separator): String {
      val fm = component.getFontMetrics(component.font)
      val symbolWidth = SwingUtilities2.stringWidth(component, fm, ellipsisSymbol)


      return when {
        symbolWidth >= maxWidth -> ""
        else -> {
          val availTextWidth = maxWidth - symbolWidth

          val separate = string.split(fileSeparatorChar)
          var str = ""
          var stringWidth = 0
          for (i in separate.lastIndex downTo 1) {
            stringWidth += SwingUtilities2.stringWidth(component, fm, separate[i] + fileSeparatorChar)
            if (stringWidth <= availTextWidth) {
              str = fileSeparatorChar + separate[i] + str
            }
          }

          return if (str.isEmpty()) "" else ellipsisSymbol + str
        }
      }
    }
  }

  private val fileSeparatorChar = File.separator

  override var longText: String
    get() = super.longText
    set(value) {
      if (value == longText) return
      super.longText = value
      val shtt = if(value.isEmpty()) "" else value.substringAfterLast(fileSeparatorChar)
      if(shtt != longText && shtt.isNotEmpty()) shortText = "$ellipsisSymbol$fileSeparatorChar$shtt"
    }

  override fun shrink(label: JComponent, fm: FontMetrics, maxWidth: Int): String {
    val prefixWidth = SwingUtilities2.stringWidth(label, fm, prefix)
    val suffixWidth = SwingUtilities2.stringWidth(label, fm, suffix)

    return when {
      maxWidth > longWidth -> {
        getLong()
      }
      longWidth > maxWidth - prefixWidth - suffixWidth -> {
        val clipString = clipString(label, longText, maxWidth - prefixWidth - suffixWidth, fileSeparatorChar)
        return if (clipString.isEmpty()) "" else "$prefix$clipString$suffix"
      }
      else -> {
        return getShort()
      }
    }
  }
}