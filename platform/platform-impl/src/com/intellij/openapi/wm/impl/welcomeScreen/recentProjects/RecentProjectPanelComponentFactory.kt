// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManager.RecentProjectsChange
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneProjectListener
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.WelcomeScreenCloneCollector
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.containers.JBTreeTraverser
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Color
import javax.swing.tree.TreeNode

@ApiStatus.Internal
object RecentProjectPanelComponentFactory {
  private const val UPDATE_INTERVAL = 50L // 50ms -- 20 frames per second

  @JvmStatic
  fun createComponent(
    parentDisposable: Disposable,
    collectors: List<() -> List<RecentProjectTreeItem>>,
    treeBackground: Color? = WelcomeScreenUIManager.getProjectsBackground(),
    disableSearchFieldBorder: Boolean = true,
  ): RecentProjectFilteringTree {
    val tree = Tree().apply {
      background = treeBackground
    }
    val taskTracker = RecentProjectProgressTracker.getInstance()
    val projectsProgress = taskTracker.projectsProgress
    val filteringTree = RecentProjectFilteringTree(tree, parentDisposable, collectors,
                                                   disableSearchFieldBorder = disableSearchFieldBorder,
                                                   projectsProgress = projectsProgress,
                                                   getProgressFor = taskTracker::getProgressFor,
                                                   onCancel = taskTracker::cancel).apply {
      installSearchField()
      expandGroups()
    }

    val modelUpdatedFlow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    fun updateTreeModel() {
      filteringTree.updateTree()
      modelUpdatedFlow.tryEmit(Unit)
    }

    val connection = ApplicationManager.getApplication().messageBus.connect(parentDisposable)
    connection.subscribe(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC, object : RecentProjectsChange {
      override fun change() {
        updateTreeModel()
      }
    })
    connection.subscribe(CloneableProjectsService.TOPIC, object : CloneProjectListener {
      override fun onCloneAdded(progressIndicator: ProgressIndicatorEx, taskInfo: TaskInfo) {
        updateTreeModel()
        WelcomeScreenCloneCollector.cloneAdded(CloneableProjectsService.getInstance().cloneCount())
      }

      override fun onCloneRemoved() {
        updateTreeModel()
      }

      override fun onCloneSuccess() {
        updateTreeModel()
        WelcomeScreenCloneCollector.cloneSuccess()
      }

      override fun onCloneFailed() {
        updateTreeModel()
        WelcomeScreenCloneCollector.cloneFailed()
      }

      override fun onCloneCanceled() {
        updateTreeModel()
        WelcomeScreenCloneCollector.cloneCanceled()
      }
    })

    val job = service<RecentProjectPanelComponentFactoryCoroutineScopeHolder>().coroutineScope.launch {
      repaintProgressBars(filteringTree, modelUpdatedFlow, projectsProgress)
    }
    Disposer.register(parentDisposable) { job.cancel() }

    return filteringTree
  }

  private suspend fun repaintProgressBars(
    filteringTree: RecentProjectFilteringTree,
    modelUpdatedFlow: Flow<Unit>,
    projectsProgress: StateFlow<ProjectsProgress>,
  ) {
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      val hasProgressBar = { collectNodesWithProgressBars(filteringTree, projectsProgress).isNotEmpty() }
      val hasProgressBarFlow = MutableStateFlow(hasProgressBar())
      launch {
        modelUpdatedFlow.collectLatest {
          hasProgressBarFlow.emit(hasProgressBar())
        }
      }

      // A task that starts or ends changes the buttons of its row, and the row shows that only once it repaints. A row whose last task
      // ended is no longer one with progress, so every row repaints rather than the ones the loop below ticks.
      launch {
        projectsProgress.collect {
          val treeModel = filteringTree.searchModel
          getAllNodes(filteringTree).forEach(treeModel::nodeChanged)
          hasProgressBarFlow.emit(hasProgressBar())
        }
      }

      launch {
        hasProgressBarFlow.collectLatest { hasProgressBars ->
          if (hasProgressBars) {
            while (isActive) {
              delay(UPDATE_INTERVAL)
              val treeModel = filteringTree.searchModel
              for (it in collectNodesWithProgressBars(filteringTree, projectsProgress)) {
                treeModel.nodeChanged(it)
              }
            }
          }
        }
      }
    }
  }

  private fun getAllNodes(filteringTree: RecentProjectFilteringTree): List<TreeNode> =
    JBTreeTraverser.from<TreeNode> { node -> TreeUtil.nodeChildren(node) }
      .withRoot(filteringTree.searchModel.root)
      .traverse()
      .toList()

  private fun collectNodesWithProgressBars(
    filteringTree: RecentProjectFilteringTree,
    projectsProgress: StateFlow<ProjectsProgress>,
  ): List<TreeNode> {
    val isCloneActive = CloneableProjectsService.getInstance().isCloneActive()
    return getAllNodes(filteringTree)
      .filter { node ->
        val userObject = TreeUtil.getUserObject(node)
        return@filter userObject is CloneableProjectItem && isCloneActive ||
                      userObject is ProviderRecentProjectItem && userObject.progressText != null ||
                      userObject is RecentProjectItem && projectsProgress.value.getProgressOf(userObject).isNotEmpty()
      }.toList()
  }
}

@Internal
@Service(Service.Level.APP)
private class RecentProjectPanelComponentFactoryCoroutineScopeHolder(@JvmField val coroutineScope: CoroutineScope)
