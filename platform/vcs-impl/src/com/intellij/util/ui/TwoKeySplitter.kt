// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.Splitter

/**
 * Same as OnePixelSplitter, but keeps vertical and horizontal proportions under separate property keys.
 */
class TwoKeySplitter(isVertical: Boolean, private val proportionKey: ProportionKey) :
  Splitter(isVertical, proportionKey.default(isVertical)) {

  private val key get() = proportionKey.key(isVertical)
  private val defaultValue get() = proportionKey.default(isVertical)

  init {
    addPropertyChangeListener(PROP_PROPORTION) { saveProportion() }
    addPropertyChangeListener(PROP_ORIENTATION) { loadProportion(defaultValue) }
    dividerWidth = 1
    isFocusable = false
  }

  override fun createDivider(): Divider = OnePixelDivider(isVertical, this)

  override fun addNotify() {
    super.addNotify()
    loadProportion(myProportion)
  }

  private fun loadProportion(default: Float) {
    proportion = PropertiesComponent.getInstance().getFloat(key, default)
  }

  private fun saveProportion() {
    PropertiesComponent.getInstance().setValue(key, myProportion, defaultValue)
  }
}

data class ProportionKey(val verticalKey: String, val verticalDefault: Float,
                         val horizontalKey: String, val horizontalDefault: Float) {
  fun key(isVertical: Boolean) = if (isVertical) verticalKey else horizontalKey
  fun default(isVertical: Boolean) = if (isVertical) verticalDefault else horizontalDefault
}