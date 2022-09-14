// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectTreeItem
import com.intellij.ui.treeStructure.Tree

/**
 * @author Konstantin Bulenkov
 */
abstract class RecentProjectsWelcomeScreenActionBase : DumbAwareAction(), LightEditCompatible {
  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  companion object {
    internal val RECENT_PROJECT_SELECTED_ITEM_KEY = DataKey.create<RecentProjectTreeItem>("RECENT_PROJECT_SELECTED_ITEM")
    internal val RECENT_PROJECT_TREE_KEY = DataKey.create<Tree>("RECENT_PROJECT_TREE")

    internal fun getSelectedItem(event: AnActionEvent): RecentProjectTreeItem? {
      return event.getData(RECENT_PROJECT_SELECTED_ITEM_KEY)
    }

    @JvmStatic
    fun getTree(event: AnActionEvent): Tree? {
      return event.getData(RECENT_PROJECT_TREE_KEY)
    }
  }
}