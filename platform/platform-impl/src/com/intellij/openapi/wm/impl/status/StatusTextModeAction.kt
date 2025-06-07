// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.ide.ui.NavBarLocation
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.ExperimentalUI
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class StatusTextModeAction : ToggleAction(), DumbAware, ActionRemoteBehaviorSpecification.Frontend {
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
    e.presentation.isVisible = e.place == ActionPlaces.STATUS_BAR_PLACE && ExperimentalUI.isNewUI()
    e.presentation.isEnabled = UISettings.getInstance().navBarLocation == NavBarLocation.BOTTOM
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
