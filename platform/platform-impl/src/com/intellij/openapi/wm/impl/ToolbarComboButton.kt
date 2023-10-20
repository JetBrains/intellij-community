// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.util.ui.JBInsets
import java.awt.Insets
import kotlin.properties.Delegates

open class ToolbarComboButton(val model: ToolbarComboButtonModel): AbstractToolbarCombo() {
  var margin: Insets by Delegates.observable(JBInsets.emptyInsets(), this::fireUpdateEvents)

  override fun getUIClassID(): String = "ToolbarComboButtonUI"

  init {
    updateUI()
    model.addChangeListener {
      invalidate()
      repaint()
    }
  }

  override fun getLeftGap(): Int = insets.left + margin.left
}