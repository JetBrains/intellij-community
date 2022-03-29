// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.ProjectGroup
import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem

/**
 * @author Konstantin Bulenkov
 */
class MoveProjectToGroupAction(private val myGroup: ProjectGroup) : RecentProjectsWelcomeScreenActionBase() {
  init {
    templatePresentation.text = myGroup.name
  }

  override fun actionPerformed(event: AnActionEvent) {
    val item = getSelectedItem(event) as RecentProjectItem
    val path = item.projectPath
    for (group in RecentProjectsManager.getInstance().groups) {
      group.removeProject(path)
      myGroup.addProject(path)
    }
  }

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = !hasGroupSelected(event)
  }
}