// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.ProjectGroup
import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.ProviderRecentProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem

/**
 * @author Konstantin Bulenkov
 */
class MoveProjectToGroupAction(private val myGroup: ProjectGroup) : RecentProjectsWelcomeScreenActionBase() {
  init {
    templatePresentation.text = myGroup.name
  }

  override fun actionPerformed(event: AnActionEvent) {
    val items = getSelectedItems(event)?.filterIsInstance<RecentProjectItem>() ?: return
    val recentProjectsManager = RecentProjectsManager.getInstance()

    items.forEach {
      recentProjectsManager.moveProjectToGroup(it.projectPath, myGroup)
    }
  }

  override fun update(event: AnActionEvent) {
    val item = getSelectedItem(event)
    event.presentation.isEnabled = item is RecentProjectItem
    event.presentation.isVisible = item !is ProviderRecentProjectItem
  }
}