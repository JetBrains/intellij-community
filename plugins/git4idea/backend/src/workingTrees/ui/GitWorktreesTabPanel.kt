// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.project.Project
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.launchOnShow
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.actions.workingTree.GitCreateWorkingTreeService
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys
import git4idea.i18n.GitBundle
import git4idea.workingTrees.GitWorktreeSupportStatus
import git4idea.workingTrees.ui.GitWorkingTreesContentProvider.Companion.GIT_WORKING_TREE_TOOLWINDOW_TAB_EMPTY_LIST
import git4idea.workingTrees.ui.GitWorkingTreesContentProvider.Companion.TOOLWINDOW_CONTENT_HELP_ID
import java.awt.Component
import java.awt.Point
import javax.swing.SwingConstants

/**
 * The Worktrees tab UI: a list of the current repository's worktrees plus the toolbar/popup actions.
 * Rendering and data are driven by [GitWorktreesTabModel]; this class only wires Swing pieces together.
 */
internal class GitWorktreesTabPanel(private val project: Project) {
  private val tabModel = GitWorktreesTabModel(project)
  private val listModel = GitWorkingTreesListModel()
  private val list = JBList(listModel).apply {
    cellRenderer = GitWorkingTreesListRenderer()
    accessibleContext.accessibleName = GitBundle.message("toolwindow.working.trees.tab.name")
    addMouseListener(createPopupHandler())
    ActionManager.getInstance().getAction("Git.WorkingTrees.Open").registerCustomShortcutSet(CommonShortcuts.ENTER, this)
  }

  val component: BorderLayoutPanel

  init {
    val scrollPane = ScrollPaneFactory.createScrollPane(list, true)

    val actionManager = ActionManager.getInstance()
    val toolbarActionGroup = actionManager.getAction("Git.WorkingTrees.ToolwindowGroup.Toolbar") as ActionGroup
    val toolbar = actionManager.createActionToolbar(GitWorkingTreesContentProvider.GIT_WORKING_TREE_TOOLWINDOW_TAB_TOOLBAR, toolbarActionGroup, false)
    toolbar.setTargetComponent(list)
    toolbar.layoutStrategy = ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY
    toolbar.setOrientation(SwingConstants.VERTICAL)

    list.launchOnShow("worktree list") {
      GitRepositoriesHolder.getInstance(project).updates.collect { event ->
        if (event == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED) {
          refresh()
        }
      }
    }

    val wrappedComponent = UiDataProvider.wrapComponent(scrollPane) { sink ->
      sink[GitWorkingTreeTabActionsDataKeys.SELECTED_WORKING_TREES] = list.selectedValuesList.map { it.gitWorkingTree }
      sink[GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY] = tabModel.currentRepository()
      sink[PlatformCoreDataKeys.HELP_ID] = TOOLWINDOW_CONTENT_HELP_ID
    }

    refresh()

    component = BorderLayoutPanel().addToCenter(wrappedComponent).addToLeft(toolbar.component)
  }

  private fun createPopupHandler(): PopupHandler = object : PopupHandler() {
    override fun invokePopup(comp: Component, x: Int, y: Int) {
      val index = list.locationToIndex(Point(x, y))
      if (index != -1 && list.getCellBounds(index, index).contains(x, y)) {
        list.selectedIndex = index
      }
      val actionGroup = ActionManager.getInstance().getAction("Git.WorkingTrees.ToolwindowGroup.Popup") as ActionGroup
      val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, actionGroup)
      popupMenu.setTargetComponent(list)
      popupMenu.component.show(comp, x, y)
    }
  }

  private fun refresh() {
    listModel.setRows(tabModel.buildRows())
    updateEmptyText()
  }

  private fun updateEmptyText() {
    val emptyText = list.emptyText
    emptyText.clear()

    when (val status = tabModel.supportStatus()) {
      is GitWorktreeSupportStatus.SingleRepository -> {
        emptyText.appendLine(GitBundle.message("toolwindow.working.trees.tab.empty.text")).withUnscaledGapAfter(20)
          .appendLine(GitBundle.message("toolwindow.working.trees.tab.empty.text.create.working.tree"),
                      SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { _ ->
            GitCreateWorkingTreeService.getInstance()
              .collectDataAndCreateWorkingTree(status.repository, null, GIT_WORKING_TREE_TOOLWINDOW_TAB_EMPTY_LIST)
          }
      }

      is GitWorktreeSupportStatus.MultipleRepository -> {
        emptyText.appendLine(GitBundle.message("toolwindow.working.trees.tab.empty.text.multirepo"))
      }

      GitWorktreeSupportStatus.Unsupported -> {}
    }

    emptyText.appendLine(AllIcons.General.ContextHelp,
                         GitBundle.message("toolwindow.working.trees.tab.empty.what.git.worktree"),
                         SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { _ ->
      HelpManager.getInstance().invokeHelp(GitWorkingTreesContentProvider.EMPTY_TAB_WORKING_TREE_CONCEPT_HELP_ID)
    }
  }
}
