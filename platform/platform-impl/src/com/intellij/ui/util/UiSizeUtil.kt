// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.util

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.NlsSafe
import java.awt.Dimension
import java.awt.Insets
import javax.swing.JComponent
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty


val Insets.width: Int get() = left + right
val Insets.height: Int get() = top + bottom

var JBPopup.width: Int by dimensionProperty(JBPopup::getSize, JBPopup::setSize, Dimension::width)
var JBPopup.height: Int by dimensionProperty(JBPopup::getSize, JBPopup::setSize, Dimension::height)

var JComponent.minimumWidth: Int by dimensionProperty(JComponent::getMinimumSize, JComponent::setMinimumSize, Dimension::width)
var JComponent.minimumHeight: Int by dimensionProperty(JComponent::getMinimumSize, JComponent::setMinimumSize, Dimension::height)
var JComponent.preferredWidth: Int by dimensionProperty(JComponent::getPreferredSize, JComponent::setPreferredSize, Dimension::width)
var JComponent.preferredHeight: Int by dimensionProperty(JComponent::getPreferredSize, JComponent::setPreferredSize, Dimension::height)
var JComponent.maximumWidth: Int by dimensionProperty(JComponent::getMaximumSize, JComponent::setMaximumSize, Dimension::width)
var JComponent.maximumHeight: Int by dimensionProperty(JComponent::getMaximumSize, JComponent::setMaximumSize, Dimension::height)

private fun <Receiver> dimensionProperty(
  getSize: Receiver.() -> Dimension,
  setSize: Receiver.(Dimension) -> Unit,
  dimensionProperty: KMutableProperty1<Dimension, Int>
): ReadWriteProperty<Receiver, Int> {
  return object : ReadWriteProperty<Receiver, Int> {

    override fun getValue(thisRef: Receiver, property: KProperty<*>): Int {
      return dimensionProperty.get(getSize(thisRef))
    }

    override fun setValue(thisRef: Receiver, property: KProperty<*>, value: Int) {
      val size = Dimension(getSize(thisRef))
      dimensionProperty.set(size, value)
      setSize(thisRef, size)
    }
  }
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
