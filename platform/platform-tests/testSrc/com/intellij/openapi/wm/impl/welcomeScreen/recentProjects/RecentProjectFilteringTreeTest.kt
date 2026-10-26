// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneStatus
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneTaskInfo
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneableProject
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestApplication
class RecentProjectFilteringTreeTest {
  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `tree collects the projects once on creation and again on each update`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      var collectionCount = 0
      val collector: () -> List<RecentProjectTreeItem> = {
        collectionCount++
        emptyList()
      }

      val filteringTree = RecentProjectPanelComponentFactory.createComponent(disposable, listOf(collector), treeBackground = null)
      // The welcome screen panels install the search field one more time.
      filteringTree.installSearchField()
      assertEquals(1, collectionCount, "collections on creation")

      filteringTree.updateTree()
      assertEquals(2, collectionCount, "collections after one update")
    }
  }

  @Test
  fun `search with no result clears the selection`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      val projectPath = Path.of("alpha")
      val cloneTaskInfo = CloneTaskInfo("", "", "", "", "", "", "", "")
      val cloneableProject = CloneableProject(projectPath, cloneTaskInfo, ProgressIndicatorBase(), CloneStatus.FAILURE)
      val project = CloneableProjectItem(projectPath, projectName = "alpha", displayName = "alpha", cloneableProject)
      val filteringTree = RecentProjectPanelComponentFactory.createComponent(disposable, listOf { listOf(project) }, treeBackground = null)
      val searchField = filteringTree.installSearchField()
      val tree = filteringTree.tree
      TreeUtil.selectNode(tree, filteringTree.root.firstChild)
      assertEquals(1, tree.selectionCount, "selected rows before the search")

      searchField.text = "zzz"
      assertEquals(0, tree.rowCount, "rows after the search with no result")
      assertTrue(tree.isSelectionEmpty, "the selection after the search with no result")
    }
  }
}
