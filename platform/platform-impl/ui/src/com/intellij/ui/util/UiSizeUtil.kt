// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.util

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.NlsSafe
import java.awt.Dimension
import java.awt.Insets
import javax.swing.JComponent

val Insets.width: Int get() = left + right
val Insets.height: Int get() = top + bottom

var JBPopup.width: Int
  get() = size.width
  @Deprecated("Prefer using [JBPopup.setSize] explicitly")
  set(newValue) {
    size = Dimension(newValue, size.height)
  }
var JBPopup.height: Int
  get() = size.height
  @Deprecated("Prefer using [JBPopup.setSize] explicitly")
  set(newValue) {
    size = Dimension(size.width, newValue)
  }

/**
 * WARNING: prefer overriding [JComponent.getPreferredSize], [JComponent.getMinimumSize], [JComponent.getMaximumSize] instead.
 *
 * Using these setters will permanently fix the second dimension as well.
 * That is: the `minimumHeight = 20` call will prevent `minimumWidth` from being updated on content changes.
 */
var JComponent.minimumWidth: Int
  get() = minimumSize.width
  @Deprecated("Override the [JComponent.getMinimumSize] or wrap into [JBPanel] instead")
  set(newValue) {
    minimumSize = Dimension(newValue, minimumSize.height)
  }
var JComponent.minimumHeight: Int
  get() = minimumSize.height
  @Deprecated("Override the [JComponent.getMinimumSize] or wrap into [JBPanel] instead")
  set(newValue) {
    minimumSize = Dimension(minimumSize.width, newValue)
  }
var JComponent.preferredWidth: Int
  get() = preferredSize.width
  @Deprecated("Override the [JComponent.getPreferredSize] or wrap into [JBPanel] instead")
  set(newValue) {
    preferredSize = Dimension(newValue, preferredSize.height)
  }
var JComponent.preferredHeight: Int
  get() = preferredSize.height
  @Deprecated("Override the [JComponent.getPreferredSize] or wrap into [JBPanel] instead")
  set(newValue) {
    preferredSize = Dimension(preferredSize.width, newValue)
  }
var JComponent.maximumWidth: Int
  get() = maximumSize.width
  @Deprecated("Override the [JComponent.getMaximumSize] or wrap into [JBPanel] instead")
  set(newValue) {
    maximumSize = Dimension(newValue, maximumSize.height)
  }
var JComponent.maximumHeight: Int
  get() = maximumSize.height
  @Deprecated("Override the [JComponent.getMaximumSize] or wrap into [JBPanel] instead")
  set(newValue) {
    maximumSize = Dimension(maximumSize.width, newValue)
  }

fun JComponent.getTextWidth(text: @NlsSafe String): Int {
  return getFontMetrics(font).stringWidth(text)
}

/**
 * @return the number of chars in [text] that fit in the given [availTextWidth].
 */
fun JComponent.getAvailTextLength(text: @NlsSafe String, availTextWidth: Int): Int {
  if (availTextWidth <= 0) return 0

  val fm = getFontMetrics(font)
  var maxBranchWidth = 0
  var maxBranchLength = 0

  for (ch in text) {
    if (maxBranchWidth >= availTextWidth) {
      maxBranchLength--
      break
    }
    else {
      maxBranchLength++
    }
    maxBranchWidth += fm.charWidth(ch)
  }

  return maxBranchLength
}
