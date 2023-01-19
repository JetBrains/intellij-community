// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.ide.ui.NavBarLocation
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.ExperimentalUI

class StatusTextModeAction : ToggleAction(), DumbAware {
  override fun isSelected(e: AnActionEvent): Boolean {
    return !UISettings.getInstance().showNavigationBarInBottom
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val settings = UISettings.getInstance()
    settings.showNavigationBar = !state
    settings.fireUISettingsChanged()
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = ExperimentalUI.isNewUI()
    e.presentation.isEnabled = UISettings.getInstance().navBarLocation == NavBarLocation.BOTTOM
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
