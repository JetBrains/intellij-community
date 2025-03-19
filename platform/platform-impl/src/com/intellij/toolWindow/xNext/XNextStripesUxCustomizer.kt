// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext

import com.intellij.ide.ui.NotRoamableUiSettings
import com.intellij.toolWindow.StripesUxCustomizer
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.xNext.toolbar.actions.XNextToolWindowButtonManager
import com.intellij.ui.ExperimentalUI

internal class XNextStripesUxCustomizer : StripesUxCustomizer() {

  override fun createCustomButtonManager(paneId: String): ToolWindowButtonManager? {
    return if (ExperimentalUI.isNewUI()) {
      return XNextToolWindowButtonManager(paneId)
    } else null
  }

  override fun updateStripesVisibility() {
    NotRoamableUiSettings.getInstance().xNextStripe = true
  }
}