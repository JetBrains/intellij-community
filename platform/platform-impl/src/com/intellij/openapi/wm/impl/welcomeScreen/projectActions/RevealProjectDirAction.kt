// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.impl.welcomeScreen.projectActions.RecentProjectsWelcomeScreenActionBase.Companion.getSelectedItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import java.nio.file.Path

class RevealProjectDirAction : DumbAwareAction(RevealFileAction.getActionName()), LightEditCompatible {
  override fun update(e: AnActionEvent) {
    val item = getSelectedItem(e)
    e.presentation.isEnabledAndVisible = item is RecentProjectItem
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    val item = getSelectedItem(e) as RecentProjectItem
    RevealFileAction.openFile(Path.of(item.projectPath))
  }
}
