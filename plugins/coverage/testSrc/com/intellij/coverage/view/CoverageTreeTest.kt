// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.view

import com.intellij.coverage.CoverageIntegrationBaseTest
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.concurrency.Invoker
import com.intellij.util.ui.tree.TreeModelAdapter
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.await
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import javax.swing.JTree
import javax.swing.tree.TreePath

@RunWith(JUnit4::class)
class CoverageTreeTest : CoverageIntegrationBaseTest() {
  @Before
  fun init() {
    registerCoverageToolWindow(myProject)
  }

  @Test
  fun `test ij coverage tree contains elements`() = testCoverageSuiteTree(loadFullSuite(), false, """
      -all
       -foo
        FooClass
        -bar
         UncoveredClass
         BarClass
       TopLevelClass
      """.trimIndent())

  @Test
  fun `test ij coverage flatten tree contains elements`() = testCoverageSuiteTree(loadFullSuite(), true, """
      -all
       -foo
        FooClass
       -
        TopLevelClass
       -foo.bar
        UncoveredClass
        BarClass
      """.trimIndent())

  @Test
  fun `test xml coverage tree contains elements`() = testCoverageSuiteTree(loadXMLSuite(), """
        -all
         -foo
          FooClass
          -bar
           UncoveredClass
           BarClass
      """.trimIndent())

  @Test
  fun `test listener receives the loaded coverage root`() = runBlocking {
    val suite = loadFullSuite()
    openSuiteAndWait(suite)
    try {
      withContext(Dispatchers.EDT) {
        val model = CoverageTableModel(suite, project, CoverageViewTreeStructure(project, suite))
        try {
          val root = requireNotNull(model.accept { TreeVisitor.Action.INTERRUPT }.await())
          val updates = mutableListOf<TreePath>()
          model.addTreeModelListenerWithCurrentRoot(TreeModelAdapter.create { event, type ->
            assertEquals(TreeModelAdapter.EventType.StructureChanged, type)
            updates.add(event.treePath)
          })
          assertEquals(listOf(root), updates)
        }
        finally {
          Disposer.dispose(model)
        }
      }
    }
    finally {
      closeSuite(suite)
    }
  }

  private fun loadFullSuite() = loadIJSuite(emptyArray(), SIMPLE_FULL_IJ_REPORT_PATH)

  private fun testCoverageSuiteTree(suite: CoverageSuitesBundle, flattenPackages: Boolean, expected: String) {
    val stateBean = CoverageViewManager.getInstance(myProject).stateBean
    val original = stateBean.isFlattenPackages
    try {
      stateBean.isFlattenPackages = flattenPackages
      testCoverageSuiteTree(suite, expected)
    }
    finally {
      stateBean.isFlattenPackages = original
    }
  }

  private fun testCoverageSuiteTree(suite: CoverageSuitesBundle, expected: String): Unit = runBlocking {
    val stateBean = CoverageViewManager.getInstance(myProject).stateBean
    stateBean.isShowOnlyModified = false
    openSuiteAndWait(suite)

    val treeStructure = CoverageViewTreeStructure(project, suite)
    val disposable = Disposer.newDisposable()
    val model = StructureTreeModel(treeStructure, null, Invoker.forEventDispatchThread(disposable), disposable)

    withContext(Dispatchers.EDT) {
      val tree = writeIntentReadAction { JTree(model) }
      TreeUtil.promiseExpandAll(tree).await()
      PlatformTestUtil.assertTreeEqual(tree, expected)
      Disposer.dispose(disposable)
      closeSuite(suite)
    }
  }
}
