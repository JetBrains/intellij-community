// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.SelectionSaver
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.plugins.github.pullrequest.action.GHPRReviewSubmitAction
import org.jetbrains.plugins.github.pullrequest.action.GHPRShowDiffAction
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JComponent
import javax.swing.JPanel

class GHPRChangesBrowserFactory(private val actionManager: ActionManager, private val project: Project) {

  fun createToolbar(target: JComponent)
    : TreeActionsToolbarPanel {

    val diffAction = GHPRShowDiffAction().apply {
      ActionUtil.copyFrom(this, IdeActions.ACTION_SHOW_DIFF_COMMON)
    }

    val reviewSubmitAction = GHPRReviewSubmitAction()
    val changesToolbarActionGroup = DefaultActionGroup(diffAction, reviewSubmitAction, Separator(),
                                                       actionManager.getAction(ChangesTree.GROUP_BY_ACTION_GROUP))
    val changesToolbar = actionManager.createActionToolbar("ChangesBrowser", changesToolbarActionGroup, true)
    val treeActionsGroup = DefaultActionGroup(actionManager.getAction(IdeActions.ACTION_EXPAND_ALL),
                                              actionManager.getAction(IdeActions.ACTION_COLLAPSE_ALL))
    return TreeActionsToolbarPanel(changesToolbar, treeActionsGroup, target)
  }

  fun createTree(parentPanel: JPanel, changesModel: SingleValueModel<List<Change>>): ChangesTree {
    val tree = object : ChangesTree(project, false, false) {
      override fun rebuildTree() {
        updateTreeModel(TreeModelBuilder(project, grouping).setChanges(changesModel.value, null).build())
        if (isSelectionEmpty && !isEmpty) TreeUtil.selectFirstNode(this)
      }

      override fun getData(dataId: String) = super.getData(dataId) ?: VcsTreeModelData.getData(project, this, dataId)

    }.also<ChangesTree> {
      SelectionSaver.installOn(it)
      it.addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) {
          if (it.isSelectionEmpty && !it.isEmpty) TreeUtil.selectFirstNode(it)
        }
      })
    }

    changesModel.addValueChangedListener(tree::rebuildTree)
    tree.rebuildTree()

    DataManager.registerDataProvider(parentPanel, tree::getData)

    val diffAction = GHPRShowDiffAction().apply {
      ActionUtil.copyFrom(this, IdeActions.ACTION_SHOW_DIFF_COMMON)
    }
    val reloadAction = actionManager.getAction("Github.PullRequest.Changes.Reload")

    diffAction.registerCustomShortcutSet(CompositeShortcutSet(diffAction.shortcutSet, CommonShortcuts.DOUBLE_CLICK_1), tree)
    reloadAction.registerCustomShortcutSet(tree, null)
    tree.installPopupHandler(DefaultActionGroup(diffAction, reloadAction))

    return tree
  }
}