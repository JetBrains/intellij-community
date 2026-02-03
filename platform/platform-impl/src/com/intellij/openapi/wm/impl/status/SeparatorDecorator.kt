// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.openapi.wm.impl.status.ProcessPopup.isProgressIndicator
import com.intellij.ui.ClientProperty
import java.awt.Component
import javax.swing.JPanel


/**
 * Places separator between two adjacent progress indicators
 * Hide separator of the first indicator, and indicators under other components (e.g., under banner from AnalyzingBannerDecorator)
 * Does not change the separator state of other components
 */
internal class SeparatorDecorator(private val panel: JPanel) {
  fun indicatorAdded() {
    placeSeparators(panel)
  }

  fun indicatorRemoved() {
    placeSeparators(panel)
  }

  fun handlePopupClose() {
    placeSeparators(panel)
  }


  companion object {
    @JvmStatic
    fun placeSeparators(panel: JPanel) {
      var previousComponentIsIndicator = false

      for (component in panel.components) {
        if (isProgressIndicator(component)) {
          component.updateSeparator(isShown = previousComponentIsIndicator)
          previousComponentIsIndicator = true
        }
        else {
          previousComponentIsIndicator = false
        }
      }
    }
  }
}

private fun Component.updateSeparator(isShown: Boolean) {
  val panel = ClientProperty.get(this, ProcessPopup.KEY)
  panel?.setSeparatorEnabled(isShown)
}

