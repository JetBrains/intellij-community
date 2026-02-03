// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.util.ui.JBInsets
import java.awt.Insets
import kotlin.properties.Delegates

open class ToolbarSplitButton(val model: ToolbarSplitButtonModel): AbstractToolbarCombo() {

  var separatorMargin: Insets by Delegates.observable(JBInsets.emptyInsets(), this::fireUpdateEvents)
  var leftPartMargin: Insets by Delegates.observable(JBInsets.emptyInsets(), this::fireUpdateEvents)
  var rightPartMargin: Insets by Delegates.observable(JBInsets.emptyInsets(), this::fireUpdateEvents)

  override fun getUIClassID(): String = "ToolbarSplitButtonUI"

  init {
    updateUI()
    model.addChangeListener {
      invalidate()
      repaint()
    }
  }

  override fun getLeftGap(): Int = insets.left + leftPartMargin.left
}