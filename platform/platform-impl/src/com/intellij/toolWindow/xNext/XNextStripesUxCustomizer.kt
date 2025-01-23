// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext

import com.intellij.ide.ui.NotRoamableUiSettings
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.toolWindow.StripesUxCustomizer
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.xNext.island.XNextToolWindowHolder
import com.intellij.toolWindow.xNext.toolbar.actions.XNextToolWindowButtonManager
import com.intellij.ui.ExperimentalUI
import java.awt.BorderLayout
import javax.swing.JComponent

internal class XNextStripesUxCustomizer : StripesUxCustomizer() {

  override fun createCustomButtonManager(paneId: String): ToolWindowButtonManager? {
    return if (ExperimentalUI.isNewUI()) {
      return XNextToolWindowButtonManager(paneId)
    } else null
  }

  override fun decorateAndReturnHolder(divider: JComponent, child: JComponent): JComponent? {
    return XNextToolWindowHolder.create().apply {
      layout = BorderLayout()
      add(divider, BorderLayout.NORTH)
      add(child, BorderLayout.CENTER)

      child.putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, true)
    }
  }

  override fun updateStripesVisibility() {
    NotRoamableUiSettings.getInstance().xNextStripe = true
  }
}