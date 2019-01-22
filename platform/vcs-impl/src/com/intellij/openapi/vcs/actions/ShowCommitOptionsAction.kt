// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vcs.changes.ChangesViewManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewCommitPanel
import com.intellij.ui.LayeredIcon

private val ICON = LayeredIcon(AllIcons.General.GearPlain, AllIcons.General.Dropdown)

class ShowCommitOptionsAction : DumbAwareAction() {
  init {
    templatePresentation.icon = ICON
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = getCommitPanel(e)?.commitOptionsPanel?.isEmpty == false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val commitPanel = getCommitPanel(e)!!
    val optionsPanel = commitPanel.commitOptionsPanel

    /*
        val balloon = JBPopupFactory.getInstance()
          .createDialogBalloonBuilder(optionsPanel, null)
          .setHideOnClickOutside(true)
          .setCloseButtonEnabled(false)
          //      .setAnimationCycle(0)
          //      .setBlockClicksThroughBalloon(true)
          .createBalloon()
    */

    val popup =
      JBPopupFactory.getInstance().createComponentPopupBuilder(optionsPanel, null)
        .setRequestFocus(true)
        .createPopup()
    popup.showCenteredInCurrentWindow(commitPanel.project)
  }

  private fun getCommitPanel(e: AnActionEvent): ChangesViewCommitPanel? {
    val changesViewManager = e.project?.let { ChangesViewManager.getInstance(it) } as? ChangesViewManager
    return changesViewManager?.commitPanel
  }
}