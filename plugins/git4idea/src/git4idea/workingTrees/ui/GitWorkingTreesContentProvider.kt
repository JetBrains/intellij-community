// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.GitWorkingTree
import git4idea.actions.workingTree.GitCreateWorkingTreeService
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitWorkingTreesService
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.Point
import java.util.function.Predicate
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.SwingConstants

internal class GitWorkingTreesContentProvider(private val project: Project) : ChangesViewContentProvider {

  companion object {
    //registered with com.intellij.statistics.actionCustomPlaceAllowlist ExtensionPoint
    internal const val GIT_WORKING_TREE_TOOLWINDOW_TAB_TOOLBAR: String = "GitWorkingTreeToolWindowTabToolbar"
    internal const val GIT_WORKING_TREE_TOOLWINDOW_TAB_EMPTY_LIST: String = "GitWorkingTreeToolWindowTabEmptyList"

    private const val TOOLWINDOW_CONTENT_HELP_ID = "worktree-help"
  }

  override fun initTabContent(content: Content) {
    content.component = GitWorkingTreesUi()
  }

  private inner class GitWorkingTreesUi : BorderLayoutPanel() {
    init {
      val list = WorkingTreesList(project)
      val scrollPane = ScrollPaneFactory.createScrollPane(list, true)
      addToCenter(scrollPane)

      val actionManager = ActionManager.getInstance()
      val toolbarActionGroup = actionManager.getAction("Git.WorkingTrees.ToolwindowGroup.Toolbar") as ActionGroup
      val toolbar = actionManager.createActionToolbar(GIT_WORKING_TREE_TOOLWINDOW_TAB_TOOLBAR,
                                                      toolbarActionGroup, false)
      toolbar.setTargetComponent(list)
      toolbar.layoutStrategy = ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY
      toolbar.setOrientation(SwingConstants.VERTICAL)
      addToLeft(toolbar.component)
    }
  }

  private class WorkingTreesList(project: Project) : BorderLayoutPanel(), UiDataProvider {
    private val model: WorkingTreesListModel = WorkingTreesListModel(project)
    private val list: JBList<GitWorkingTree> = JBList(model)

    init {
      list.cellRenderer = WorkingTreesListRenderer()
      initEmptyText(list.emptyText)
      addToCenter(list)

      list.addMouseListener(object : PopupHandler() {
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
      })

      GitWorkingTreesService.getInstance(project).coroutineScope.launch {
        GitRepositoriesHolder.getInstance(project).updates.collect { event ->
          if (event == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED) {
            ApplicationManager.getApplication().invokeLater {
              model.reload(project)
            }
          }
        }
      }
    }

    private fun initEmptyText(emptyText: StatusText) {
      emptyText.text = GitBundle.message("toolwindow.working.trees.tab.empty.text")
      emptyText.appendLine(GitBundle.message("toolwindow.working.trees.tab.empty.text.create.working.tree"),
                           SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) { _ ->
        val repository = model.repository
        if (repository != null) {
          GitCreateWorkingTreeService.getInstance().collectDataAndCreateWorkingTree(repository,
                                                                                    null,
                                                                                    GIT_WORKING_TREE_TOOLWINDOW_TAB_EMPTY_LIST)
        }
      }
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink[GitWorkingTreeTabActionsDataKeys.SELECTED_WORKING_TREES] = list.selectedValuesList
      sink[GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY] = model.repository
      sink[PlatformCoreDataKeys.HELP_ID] = TOOLWINDOW_CONTENT_HELP_ID
    }
  }

  private class WorkingTreesListModel(project: Project) : DefaultListModel<GitWorkingTree>() {
    var repository: GitRepository? = null
      private set

    init {
      reload(project)
    }

    fun reload(project: Project) {
      clear()
      val currentRepository = GitWorkingTreesService.getRepoForWorkingTreesSupport(project)
      repository = currentRepository
      val workingTrees = currentRepository?.workingTreeHolder?.getWorkingTrees()
      if (workingTrees != null && workingTrees.size > 1) {
        workingTrees.forEach {
          if (it.isMain) {
            add(0, it)
          }
          else {
            addElement(it)
          }
        }
      }
    }
  }

  private class WorkingTreesListRenderer : ColoredListCellRenderer<GitWorkingTree>() {
    override fun customizeCellRenderer(list: JList<out GitWorkingTree?>, value: GitWorkingTree?, index: Int, selected: Boolean, hasFocus: Boolean) {
      if (value == null) return
      if (value.isCurrent) {
        icon = AllIcons.Actions.Checked
      }
      else {
        icon = AllIcons.Empty
      }
      append(" ")
      append(value.path.name,
             if (value.isMain) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES)
      append("   ")
      val presentableBranchName = when (val branch = value.currentBranch) {
        null -> GitBundle.message("toolwindow.working.trees.tab.detached.working.tree.branch.text")
        else -> branch.name
      }
      append(presentableBranchName, SimpleTextAttributes.GRAY_ATTRIBUTES)
    }
  }
}

internal class GitWorkingTreesContentPreloader(val project: Project) : ChangesViewContentProvider.Preloader {
  override fun preloadTabContent(content: Content) {
    content.putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY, ChangesViewContentManager.TabOrderWeight.WORKING_TREES.weight)

    content.isCloseable = true
    content.displayName = GitBundle.message("toolwindow.working.trees.tab.name")
    // content.manager is not yet initialized here
    ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS)?.contentManager?.addContentManagerListener(object : ContentManagerListener {
      override fun contentRemoved(event: ContentManagerEvent) {
        if (event.content == content) {
          GitWorkingTreesService.getInstance(project).workingTreesTabClosedByUser()
        }
      }
    })
  }
}

internal class GitWorkingTreesContentVisibilityPredicate : Predicate<Project> {
  override fun test(project: Project): Boolean {
    val shouldWorkingTreesTabBeShown = GitWorkingTreesService.getInstance(project).shouldWorkingTreesTabBeShown()
    return shouldWorkingTreesTabBeShown
  }
}

