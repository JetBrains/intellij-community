// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem

/**
 * @author Konstantin Bulenkov
 */
class RemoveSelectedProjectsFromGroupsAction : RecentProjectsWelcomeScreenActionBase() {
  init {
    templatePresentation.setText(IdeBundle.messagePointer("action.presentation.RemoveSelectedProjectsFromGroupsAction.text"))
  }

  override fun actionPerformed(e: AnActionEvent) {
    val item = getSelectedItem(e)
    if (item is RecentProjectItem) {
      for (group in RecentProjectsManager.getInstance().groups) {
        group.removeProject(item.projectPath)
      }
    }
  }
}