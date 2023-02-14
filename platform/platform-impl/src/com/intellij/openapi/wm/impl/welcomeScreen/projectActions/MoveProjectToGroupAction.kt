// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.ProjectGroup
import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem
import com.intellij.util.asSafely

/**
 * @author Konstantin Bulenkov
 */
class MoveProjectToGroupAction(private val myGroup: ProjectGroup) : RecentProjectsWelcomeScreenActionBase() {
  init {
    templatePresentation.text = myGroup.name
  }

  override fun actionPerformed(event: AnActionEvent) {
    val item = getSelectedItem(event).asSafely<RecentProjectItem>() ?: return
    val path = item.projectPath
    val recentProjectsManager = RecentProjectsManager.getInstance()
    recentProjectsManager.moveProjectToGroup(path, myGroup)
  }

  override fun update(event: AnActionEvent) {
    val item = getSelectedItem(event)
    event.presentation.isEnabled = item is RecentProjectItem
  }
}