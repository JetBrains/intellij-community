// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.RecentProjectsManager.RecentProjectsChange
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.impl.welcomeScreen.ProjectDetector
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneProjectListener
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.WelcomeScreenCloneCollector
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Alarm
import com.intellij.util.containers.JBTreeTraverser
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import javax.swing.tree.TreeNode

internal object RecentProjectPanelComponentFactory {
  private const val UPDATE_INTERVAL = 50 // 50ms -- 20 frames per second

  @JvmStatic
  fun createComponent(parentDisposable: Disposable, collectors: List<() -> List<RecentProjectTreeItem>>): RecentProjectFilteringTree {
    ProjectDetector.runDetectors {} // Run detectors that will add projects to the RecentProjectsManagerBase

    val tree = Tree()
    val filteringTree = RecentProjectFilteringTree(tree, parentDisposable, collectors).apply {
      installSearchField()
      expandGroups()
    }

    ApplicationManager.getApplication().messageBus.connect(parentDisposable).apply {
      subscribe(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC, object : RecentProjectsChange {
        override fun change() {
          filteringTree.updateTree()
        }
      })
      subscribe(CloneableProjectsService.TOPIC, object : CloneProjectListener {
        override fun onCloneAdded(progressIndicator: ProgressIndicatorEx, taskInfo: TaskInfo) {
          filteringTree.updateTree()
          WelcomeScreenCloneCollector.cloneAdded(CloneableProjectsService.getInstance().cloneCount())
        }

        override fun onCloneRemoved() {
          filteringTree.updateTree()
        }

        override fun onCloneSuccess() {
          filteringTree.updateTree()
          WelcomeScreenCloneCollector.cloneSuccess()
        }

        override fun onCloneFailed() {
          filteringTree.updateTree()
          WelcomeScreenCloneCollector.cloneFailed()
        }

        override fun onCloneCanceled() {
          filteringTree.updateTree()
          WelcomeScreenCloneCollector.cloneCanceled()
        }
      })
    }

    val updateQueue = MergingUpdateQueue("Welcome screen UI updater", UPDATE_INTERVAL, true, null,
                                         parentDisposable, tree, Alarm.ThreadToUse.SWING_THREAD)
    updateQueue.queue(Update.create(filteringTree, Runnable { repaintProgressBars(updateQueue, filteringTree) }))

    return filteringTree
  }

  private fun repaintProgressBars(updateQueue: MergingUpdateQueue, filteringTree: RecentProjectFilteringTree) {
    val cloneableProjectsService = CloneableProjectsService.getInstance()
    if (cloneableProjectsService.isCloneActive()) {
      val model = filteringTree.searchModel
      JBTreeTraverser.from<TreeNode> { node -> TreeUtil.nodeChildren(node) }
        .withRoot(model.root)
        .traverse()
        .filter { node -> TreeUtil.getUserObject(CloneableProjectItem::class.java, node) != null }
        .forEach { node -> model.nodeChanged(node) }
    }

    updateQueue.queue(Update.create(filteringTree, Runnable { repaintProgressBars(updateQueue, filteringTree) }))
  }
}