// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.switcher.QuickActionProvider
import javax.swing.JComponent

internal class ComponentQuickActionProvider(private val component: JComponent) : QuickActionProvider {
  override fun getActions(originalProvider: Boolean): List<AnAction> {
    return SimpleToolWindowPanel.collectActions(component)
  }

  override fun getComponent() = component
  override fun getName() = null
}
