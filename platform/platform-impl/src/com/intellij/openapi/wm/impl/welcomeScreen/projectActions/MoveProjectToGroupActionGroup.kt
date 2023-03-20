// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.wm.impl.welcomeScreen.projectActions.RecentProjectsWelcomeScreenActionBase.Companion.getSelectedItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.CloneableProjectItem
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectItem

/**
 * @author Konstantin Bulenkov
 */
class MoveProjectToGroupActionGroup : DefaultActionGroup(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    if (e == null) return AnAction.EMPTY_ARRAY

    val item = getSelectedItem(e)
    if (item is RecentProjectItem || item is CloneableProjectItem) {
      val result = mutableListOf<AnAction>()
      val groups = RecentProjectsManager.getInstance().groups.sortedWith(compareBy(NaturalComparator.INSTANCE) { it.name })
      for (group in groups) {
        if (group.isTutorials) {
          continue
        }
        result.add(MoveProjectToGroupAction(group))
      }
      if (groups.isNotEmpty()) {
        result.add(Separator.getInstance())
        result.add(RemoveSelectedProjectsFromGroupsAction())
      }
      return result.toTypedArray()
    }

    return AnAction.EMPTY_ARRAY
  }
}