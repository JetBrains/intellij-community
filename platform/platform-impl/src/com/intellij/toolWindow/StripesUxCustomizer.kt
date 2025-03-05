// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.ide.ui.NotRoamableUiSettings
import javax.swing.JComponent

internal open class StripesUxCustomizer {
  init {
    updateStripesVisibility()
  }

  protected open fun updateStripesVisibility() {
    NotRoamableUiSettings.getInstance().xNextStripe = false
  }

  open fun createCustomButtonManager(paneId: String): ToolWindowButtonManager? = null

  open fun decorateAndReturnHolder(divider: JComponent, child: JComponent): JComponent? = null
}
