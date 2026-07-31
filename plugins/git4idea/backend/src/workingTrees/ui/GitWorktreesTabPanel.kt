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
import com.intellij.openapi.application.EDT
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.project.Project
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.launchOnShow
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.i18n.GitBundle
import git4idea.workingTrees.GitCreateWorkingTreeService
import git4idea.workingTrees.ui.actions.GitWorkingTreeTabActionsDataKeys
import git4idea.workingTrees.ui.GitWorkingTreesContentProvider.Companion.GIT_WORKING_TREE_TOOLWINDOW_TAB_EMPTY_LIST
import git4idea.workingTrees.ui.GitWorkingTreesContentProvider.Companion.TOOLWINDOW_CONTENT_HELP_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.ToolTipManager

/**
 * The Worktrees tab UI. Renders the grouped worktree list from [GitWorktreesTabModel] (which reads the shared
 * repository model) and publishes the selection to the toolbar/popup actions.
 */
internal class GitWorktreesTabPanel(private val project: Project) {
  private val tabModel = GitWorktreesTabModel(project)
  private val listModel = GitWorkingTreesListModel()
  private val list = object : JBList<GitWorkingTreesListEntry>(listModel) {
    // Explain the submodule re-link caveat on hover of a submodule repository header.
    override fun getToolTipText(event: MouseEvent): String? {
      val index = locationToIndex(event.point)
      if (index < 0 || !getCellBounds(index, index).contains(event.point)) return null
      val header = model.getElementAt(index) as? GitRepositoryHeader ?: return null
      return if (header.kind == GitRepositoryKind.SUBMODULE) GitBundle.message("toolwindow.working.trees.submodule.relink.warning")
      else null
    }
  }.apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = GitWorkingTreesListRenderer()
    accessibleContext.accessibleName = GitBundle.message("toolwindow.working.trees.tab.name")
    addMouseListener(createPopupHandler())
    ActionManager.getInstance().getAction("Git.WorkingTrees.Open").registerCustomShortcutSet(CommonShortcuts.ENTER, this)
    ToolTipManager.sharedInstance().registerComponent(this)
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
      refresh()
      // collectLatest: a burst of updates cancels an in-flight rebuild so a stale one can't apply last.
      GitRepositoriesHolder.getInstance(project).updates.collectLatest { event ->
        when (event) {
          GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED,
          GitRepositoriesHolder.UpdateType.RELOAD_STATE,
          GitRepositoriesHolder.UpdateType.REPOSITORY_CREATED,
          GitRepositoriesHolder.UpdateType.REPOSITORY_DELETED -> refresh()
          else -> {}
        }
      }
    }

    val wrappedComponent = UiDataProvider.wrapComponent(scrollPane) { sink ->
      val selected = list.selectedValuesList
      sink[GitWorkingTreeTabActionsDataKeys.SELECTED_WORKING_TREES] =
        selected.filterIsInstance<GitWorktreeRow>().map { it.gitWorkingTree }
      sink[GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY] = tabModel.selectedBackendRepository(selected)
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

  private suspend fun refresh() {
    val entries = tabModel.buildEntries()
    withContext(Dispatchers.EDT) {
      applyEntries(entries)
    }
  }

  private fun applyEntries(entries: List<GitWorkingTreesListEntry>) {
    val previouslySelectedPath = (list.selectedValue as? GitWorktreeRow)?.gitWorkingTree?.path

    listModel.setEntries(entries)

    val indexToSelect = (0 until listModel.size()).firstOrNull { i ->
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
    val currentIndex = (0 until listModel.size()).firstOrNull { i ->
      (listModel.getElementAt(i) as? GitWorktreeRow)?.gitWorkingTree?.isCurrent == true
    }
    val rowIndex = currentIndex ?: (0 until listModel.size()).firstOrNull { i ->
      listModel.getElementAt(i) is GitWorktreeRow
    }
    if (rowIndex != null) {
      list.selectedIndex = rowIndex
    }
  }

  private fun updateEmptyText() {
    val emptyText = list.emptyText
    emptyText.clear()

    val singleRepository = tabModel.repositories().singleOrNull()
    if (singleRepository != null) {
      emptyText.appendLine(GitBundle.message("toolwindow.working.trees.tab.empty.text")).withUnscaledGapAfter(20)
        .appendLine(GitBundle.message("toolwindow.working.trees.tab.empty.text.create.working.tree"),
                    SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { _ ->
          val repository = tabModel.backendRepository(singleRepository) ?: return@appendLine
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
