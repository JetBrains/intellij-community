// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRReviewSubmitAction
import org.jetbrains.plugins.github.pullrequest.action.GHPRShowDiffAction
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanel
import javax.swing.JComponent
import javax.swing.JPanel

object GHPRChangesBrowser {

  fun create(project: Project,
             changesModel: GHPRChangesModel,
             diffHelper: GHPRChangesDiffHelper,
             @Nls(capitalization = Nls.Capitalization.Sentence) panelEmptyText: String,
             disposable: Disposable): JComponent {

    val actionManager = ActionManager.getInstance()

    val diffAction = GHPRShowDiffAction().apply {
      ActionUtil.copyFrom(this, IdeActions.ACTION_SHOW_DIFF_COMMON)
    }
    val reloadAction = actionManager.getAction("Github.PullRequest.Changes.Reload")

    val changesTreePanel = GHPRChangesTree.createLazyTreePanel(changesModel) {
      createTree(project, changesModel, diffAction, diffHelper, reloadAction, it, disposable).apply {
        emptyText.text = panelEmptyText
      }
    }.apply {
      emptyText.text = panelEmptyText
    }

    val toolbar = createToolbar(actionManager, diffAction, changesTreePanel)
    val scrollPane = ScrollPaneFactory.createScrollPane(changesTreePanel, SideBorder.TOP)

    return BorderLayoutPanel().andTransparent()
      .addToTop(toolbar)
      .addToCenter(scrollPane)
  }

  private fun createToolbar(actionManager: ActionManager, diffAction: GHPRShowDiffAction, target: JComponent)
    : TreeActionsToolbarPanel {

    val reviewSubmitAction = GHPRReviewSubmitAction()
    val changesToolbarActionGroup = DefaultActionGroup(diffAction, reviewSubmitAction, Separator(),
                                                       actionManager.getAction(ChangesTree.GROUP_BY_ACTION_GROUP))
    val changesToolbar = actionManager.createActionToolbar("ChangesBrowser", changesToolbarActionGroup, true)
    val treeActionsGroup = DefaultActionGroup(actionManager.getAction(IdeActions.ACTION_EXPAND_ALL),
                                              actionManager.getAction(IdeActions.ACTION_COLLAPSE_ALL))
    return TreeActionsToolbarPanel(changesToolbar, treeActionsGroup, target)
  }

  fun create(project: Project,
             loadingModel: GHLoadingModel,
             changesModel: GHPRChangesModel,
             diffHelper: GHPRChangesDiffHelper,
             @Nls(capitalization = Nls.Capitalization.Sentence) panelEmptyText: String = "",
             disposable: Disposable): JComponent {

    val actionManager = ActionManager.getInstance()

    val diffAction = GHPRShowDiffAction().apply {
      ActionUtil.copyFrom(this, IdeActions.ACTION_SHOW_DIFF_COMMON)
    }
    val reloadAction = actionManager.getAction("Github.PullRequest.Changes.Reload")

    val loadingPanel = GHLoadingPanel.create(loadingModel, {
      createTree(project, changesModel, diffAction, diffHelper, reloadAction, it, disposable).apply {
        emptyText.text = panelEmptyText
        ScrollPaneFactory.createScrollPane(this, SideBorder.TOP)
      }
    }, disposable, GithubBundle.message("cannot.load.changes"))

    val toolbar = createToolbar(actionManager, diffAction, loadingPanel)

    return BorderLayoutPanel().andTransparent()
      .addToTop(toolbar)
      .addToCenter(loadingPanel)
  }

  private fun createTree(project: Project, changesModel: GHPRChangesModel,
                         diffAction: GHPRShowDiffAction, diffHelper: GHPRChangesDiffHelper, reloadAction: AnAction,
                         parentPanel: JPanel,
                         disposable: Disposable) =
    GHPRChangesTree.create(project, changesModel).also {
      DataManager.registerDataProvider(parentPanel) { dataId ->
        when {
          GHPRChangesDiffHelper.DATA_KEY.`is`(dataId) -> diffHelper
          else -> it.getData(dataId)
        }
      }

      diffAction.registerCustomShortcutSet(CompositeShortcutSet(diffAction.shortcutSet, CommonShortcuts.DOUBLE_CLICK_1), it)
      reloadAction.registerCustomShortcutSet(it, disposable)
      it.installPopupHandler(DefaultActionGroup(diffAction, reloadAction))
    }
}