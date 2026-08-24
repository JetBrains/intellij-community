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
import com.intellij.openapi.application.UI
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionListModel
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.launchOnShow
import git4idea.i18n.GitBundle
import git4idea.workingTrees.GitCreateWorkingTreeService
import git4idea.workingTrees.ui.actions.GitWorkingTreeTabActionsDataKeys
import git4idea.workingTrees.ui.GitWorkingTreesContentProvider.Companion.GIT_WORKING_TREE_TOOLWINDOW_TAB_EMPTY_LIST
import git4idea.workingTrees.ui.GitWorkingTreesContentProvider.Companion.TOOLWINDOW_CONTENT_HELP_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.Point
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

/**
 * The Worktrees tab UI. Renders the grouped worktree list from [GitWorktreesViewModel] (which reads the shared
 * repository model) and publishes the selection to the toolbar/popup actions.
 */
internal class GitWorktreesTabPanel(private val project: Project, cs: CoroutineScope) {
  private val viewModel = GitWorktreesViewModel(project, cs)
  private val listModel = CollectionListModel<GitWorkingTreesListEntry>()
  private val list = JBList<GitWorkingTreesListEntry>(listModel).apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = GitWorkingTreesListRenderer()
    accessibleContext.accessibleName = GitBundle.message("toolwindow.working.trees.tab.name")
    addMouseListener(createPopupHandler())
    ActionManager.getInstance().getAction("Git.WorkingTrees.Open").registerCustomShortcutSet(CommonShortcuts.ENTER, this)
  }

  val component: JComponent

  init {
    val scrollPane = ScrollPaneFactory.createScrollPane(list, true)

    val actionManager = ActionManager.getInstance()
    val toolbarActionGroup = actionManager.getAction("Git.WorkingTrees.ToolwindowGroup.Toolbar") as ActionGroup
    val toolbar = actionManager.createActionToolbar(
      GitWorkingTreesContentProvider.GIT_WORKING_TREE_TOOLWINDOW_TAB_TOOLBAR,
      toolbarActionGroup,
      false,
    ).apply {
      setTargetComponent(list)
      layoutStrategy = ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY
      setOrientation(SwingConstants.VERTICAL)
    }

    list.launchOnShow("worktree list") {
      viewModel.entries.collect { entries ->
        withContext(Dispatchers.UI) {
          applyEntries(entries)
        }
      }
    }

    val wrappedComponent = UiDataProvider.wrapComponent(scrollPane) { sink ->
      val selected = list.selectedValuesList
      sink[GitWorkingTreeTabActionsDataKeys.SELECTED_WORKING_TREES] =
        selected.filterIsInstance<GitWorktreeRow>().map { it.gitWorkingTree }
      sink[GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY] = GitWorktreesUiUtil.resolveSelectedBackendRepository(project, selected)
      sink[PlatformCoreDataKeys.HELP_ID] = TOOLWINDOW_CONTENT_HELP_ID
    }

    component = BorderLayoutPanel().addToCenter(wrappedComponent).addToLeft(toolbar.component)
  }

  private fun createPopupHandler(): PopupHandler = object : PopupHandler() {
    override fun invokePopup(comp: Component, x: Int, y: Int) {
      val index = list.locationToIndex(Point(x, y))
      if (index == -1 || !list.getCellBounds(index, index).contains(x, y)) return
      list.selectedIndex = index

      // A repository header offers repository-scoped actions (create a worktree); a worktree row offers
      // worktree-scoped actions (open, delete).
      val groupId = when (listModel.getElementAt(index)) {
        is GitRepositoryHeader -> "Git.WorkingTrees.ToolwindowGroup.Popup.Repository"
        is GitWorktreeRow -> "Git.WorkingTrees.ToolwindowGroup.Popup"
      }
      val actionGroup = ActionManager.getInstance().getAction(groupId) as ActionGroup
      val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, actionGroup)
      popupMenu.setTargetComponent(list)
      popupMenu.component.show(comp, x, y)
    }
  }

  private fun applyEntries(entries: List<GitWorkingTreesListEntry>) {
    val previouslySelectedPath = (list.selectedValue as? GitWorktreeRow)?.gitWorkingTree?.path

    listModel.replaceAll(entries)

    val indexToSelect = (0 until listModel.size).firstOrNull { i ->
      (listModel.getElementAt(i) as? GitWorktreeRow)?.gitWorkingTree?.path == previouslySelectedPath
    }
    if (indexToSelect != null) {
      list.selectedIndex = indexToSelect
    }
    else {
      selectDefaultEntry()
    }

    updateEmptyText()
  }

  private fun selectDefaultEntry() {
    val currentIndex = (0 until listModel.size).firstOrNull { i ->
      (listModel.getElementAt(i) as? GitWorktreeRow)?.gitWorkingTree?.isCurrent == true
    }
    val rowIndex = currentIndex ?: (0 until listModel.size).firstOrNull { i ->
      listModel.getElementAt(i) is GitWorktreeRow
    }
    if (rowIndex != null) {
      list.selectedIndex = rowIndex
    }
  }

  private fun updateEmptyText() {
    val emptyText = list.emptyText
    emptyText.clear()

    val singleRepository = GitWorktreesUiUtil.getRepositories(project).singleOrNull()
    if (singleRepository != null) {
      emptyText.appendLine(GitBundle.message("toolwindow.working.trees.tab.empty.text")).withUnscaledGapAfter(20)
        .appendLine(GitBundle.message("toolwindow.working.trees.tab.empty.text.create.working.tree"),
                    SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { _ ->
          val repository = GitWorktreesUiUtil.findBackendRepository(project, singleRepository) ?: return@appendLine
          GitCreateWorkingTreeService.getInstance()
            .collectDataAndCreateWorkingTree(repository, null, GIT_WORKING_TREE_TOOLWINDOW_TAB_EMPTY_LIST)
        }
    }
    else {
      emptyText.appendLine(GitBundle.message("toolwindow.working.trees.tab.empty.text"))
    }

    emptyText.appendLine(AllIcons.General.ContextHelp,
                         GitBundle.message("toolwindow.working.trees.tab.empty.what.git.worktree"),
                         SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { _ ->
      HelpManager.getInstance().invokeHelp(GitWorkingTreesContentProvider.EMPTY_TAB_WORKING_TREE_CONCEPT_HELP_ID)
    }
  }
}
