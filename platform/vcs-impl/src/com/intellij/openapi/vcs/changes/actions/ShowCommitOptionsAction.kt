// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vcs.changes.ui.CommitOptionsPanel
import com.intellij.ui.LayeredIcon

val ICON = LayeredIcon(AllIcons.General.GearPlain, AllIcons.General.Dropdown)

class ShowCommitOptionsAction : DumbAwareAction() {
  init {
    templatePresentation.icon = ICON
  }

  override fun actionPerformed(e: AnActionEvent) {
    val panel = CommitOptionsPanel(null, null, null)
    val balloon = JBPopupFactory.getInstance()
      .createDialogBalloonBuilder(panel, null)
      .setHideOnClickOutside(true)
      .setCloseButtonEnabled(false)
      //      .setAnimationCycle(0)
      //      .setBlockClicksThroughBalloon(true)
      .createBalloon()

  }
}