// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.ui.ThreeComponentsSplitter
import com.intellij.openapi.util.registry.Registry
import javax.swing.JComponent

internal class ToolWindowsPaneThreeSplitterHolder(vertical: Boolean = false, onePixelDividers: Boolean = false) {
  val splitter: ThreeComponentsSplitter = ThreeComponentsSplitter(vertical, onePixelDividers)

  var firstComponent: JComponent? = null
    get() = splitter.firstComponent
    set(value) {
      if (field === value) {
        return
      }
      field = value
      splitter.firstComponent = value
    }
  var innerComponent: JComponent? = null
    get() = splitter.innerComponent
    set(value) {
      if (field === value) {
        return
      }
      field = value
      splitter.innerComponent = value
    }
  var lastComponent: JComponent? = null
    get() = splitter.lastComponent
    set(value) {
      if (field === value) {
        return
      }
      field = value
      splitter.lastComponent = value
    }

  init {
    splitter.dividerWidth = 0
    splitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"))

    splitter.background = InternalUICustomization.getInstance().getToolWindowsPaneThreeSplitterBackground()
  }

}