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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Color
import javax.swing.tree.TreeNode

@ApiStatus.Internal
object RecentProjectPanelComponentFactory {
  private const val UPDATE_INTERVAL = 50L // 50ms -- 20 frames per second

  @JvmStatic
  fun createComponent(parentDisposable: Disposable, collectors: List<() -> List<RecentProjectTreeItem>>,
                      treeBackground: Color? = WelcomeScreenUIManager.getProjectsBackground()
  ): RecentProjectFilteringTree {
    val tree = Tree().apply {
      background = treeBackground
    }
    val filteringTree = RecentProjectFilteringTree(tree, parentDisposable, collectors).apply {
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
      repaintProgressBars(filteringTree, modelUpdatedFlow)
    }
    Disposer.register(parentDisposable) { job.cancel() }

    return filteringTree
  }

  private suspend fun repaintProgressBars(
    filteringTree: RecentProjectFilteringTree,
    modelUpdatedFlow: Flow<Unit>,
  ) {
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      val hasProgressBar = { collectNodesWithProgressBars(filteringTree).isNotEmpty() }
      val hasProgressBarFlow = MutableStateFlow(hasProgressBar())
      launch {
        modelUpdatedFlow.collectLatest {
          hasProgressBarFlow.emit(hasProgressBar())
        }
      }

      launch {
        hasProgressBarFlow.collectLatest { hasProgressBars ->
          if (hasProgressBars) {
            while (isActive) {
              delay(UPDATE_INTERVAL)
              val treeModel = filteringTree.searchModel
              for (it in collectNodesWithProgressBars(filteringTree)) {
                treeModel.nodeChanged(it)
              }
            }
          }
        }
      }
    }
  }

  private fun collectNodesWithProgressBars(filteringTree: RecentProjectFilteringTree): List<TreeNode> {
    val isCloneActive = CloneableProjectsService.getInstance().isCloneActive()
    val model = filteringTree.searchModel
    return JBTreeTraverser.from<TreeNode> { node -> TreeUtil.nodeChildren(node) }
      .withRoot(model.root)
      .traverse()
      .filter { node ->
        val userObject = TreeUtil.getUserObject(node)
        return@filter userObject is CloneableProjectItem && isCloneActive ||
                      userObject is ProviderRecentProjectItem && userObject.progressText != null
      }.toList()
  }
}

@Internal
@Service(Service.Level.APP)
private class RecentProjectPanelComponentFactoryCoroutineScopeHolder(@JvmField val coroutineScope: CoroutineScope)
