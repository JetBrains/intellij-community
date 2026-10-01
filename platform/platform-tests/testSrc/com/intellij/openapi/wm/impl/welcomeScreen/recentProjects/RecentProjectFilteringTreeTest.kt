// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
}
