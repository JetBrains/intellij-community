// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.impl.welcomeScreen.projectActions.RecentProjectsWelcomeScreenActionBase.Companion.getSelectedItems
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import java.nio.file.Path
import kotlin.io.path.isDirectory

@Suppress("ActionPresentationInstantiatedInCtor")
class RevealProjectDirAction : DumbAwareAction(RevealFileAction.getActionName()), LightEditCompatible, ActionRemoteBehaviorSpecification.Disabled {
  override fun update(e: AnActionEvent) {
    val hasItemsToOpen = getSelectedItems(e)?.any { it is RecentProjectItem } == true
    e.presentation.isEnabled = RevealFileAction.isDirectoryOpenSupported() && hasItemsToOpen
    e.presentation.isVisible = hasItemsToOpen
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    getSelectedItems(e)?.filterIsInstance<RecentProjectItem>()?.forEach { item ->
      val projectPath = Path.of(item.projectPath)
      when {
        RevealFileAction.isSupported() -> RevealFileAction.openFile(projectPath)
        projectPath.isDirectory() -> RevealFileAction.openDirectory(projectPath)
        else -> RevealFileAction.openDirectory(projectPath.parent!!)
      }
    }
  }
}
