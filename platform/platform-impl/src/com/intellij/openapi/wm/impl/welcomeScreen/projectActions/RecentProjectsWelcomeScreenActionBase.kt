// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.projectActions

import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.RecentProjectTreeItem
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.castSafelyTo
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * @author Konstantin Bulenkov
 */
abstract class RecentProjectsWelcomeScreenActionBase : DumbAwareAction(), LightEditCompatible {
  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  companion object {
    @JvmStatic
    fun getDataModel(event: AnActionEvent): DefaultTreeModel? {
      val tree = getTree(event)
      if (tree != null) {
        val model = tree.model
        if (model is DefaultTreeModel) {
          return model
        }
      }

      return null
    }

    internal fun getSelectedItem(event: AnActionEvent): RecentProjectTreeItem? {
      val tree = getTree(event)
      val node = tree?.selectionPath?.lastPathComponent.castSafelyTo<DefaultMutableTreeNode>()
                 ?: return null

      return node.userObject as? RecentProjectTreeItem
    }

    @JvmStatic
    fun getTree(event: AnActionEvent): Tree? {
      val component = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
      return if (component is Tree) component
      else null
    }
  }
}