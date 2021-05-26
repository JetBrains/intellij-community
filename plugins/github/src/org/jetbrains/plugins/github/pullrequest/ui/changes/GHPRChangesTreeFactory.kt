// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.ui.SelectionSaver
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JComponent

internal class GHPRChangesTreeFactory(private val project: Project,
                                      private val changesModel: SingleValueModel<out Collection<Change>>) {

  fun create(emptyTextText: String): ChangesTree {
    val tree = object : ChangesTree(project, false, false) {
      override fun rebuildTree() {
        updateTreeModel(TreeModelBuilder(project, grouping).setChanges(changesModel.value, null).build())
        if (isSelectionEmpty && !isEmpty) TreeUtil.selectFirstNode(this)
      }

      override fun getData(dataId: String) = super.getData(dataId) ?: VcsTreeModelData.getData(project, this, dataId)

    }.apply {
      emptyText.text = emptyTextText
    }.also {
      UIUtil.putClientProperty(it, ExpandableItemsHandler.IGNORE_ITEM_SELECTION, true)
      SelectionSaver.installOn(it)
      it.addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) {
          if (it.isSelectionEmpty && !it.isEmpty) TreeUtil.selectFirstNode(it)
        }
      })
    }
    changesModel.addAndInvokeValueChangedListener(tree::rebuildTree)
    return tree
  }

  companion object {
    fun createTreeToolbar(actionManager: ActionManager, treeContainer: JComponent): JComponent {
      val changesToolbarActionGroup = actionManager.getAction("Github.PullRequest.Changes.Toolbar") as ActionGroup
      val changesToolbar = actionManager.createActionToolbar("ChangesBrowser", changesToolbarActionGroup, true)
      val treeActionsGroup = DefaultActionGroup(actionManager.getAction(IdeActions.ACTION_EXPAND_ALL),
                                                actionManager.getAction(IdeActions.ACTION_COLLAPSE_ALL))
      return TreeActionsToolbarPanel(changesToolbar, treeActionsGroup, treeContainer)
    }
  }
}