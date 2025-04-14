// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures

import com.intellij.build.*
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.platform.testFramework.assertion.treeAssertion.buildTree
import com.intellij.testFramework.*
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.tree.TreeUtil
import junit.framework.TestCase
import junit.framework.TestCase.assertEquals
import org.jetbrains.annotations.NotNull
import javax.swing.tree.DefaultMutableTreeNode

class BuildViewTestFixture(private val myProject: Project) : IdeaTestFixture {
  @Suppress("ObjectLiteralToLambda")
  private val fixtureDisposable: Disposable = object : Disposable {
    override fun dispose() {}
  }

  private lateinit var syncViewManager: TestSyncViewManager
  private lateinit var buildViewManager: TestBuildViewManager

  @Throws(Exception::class)
  override fun setUp() {
    myProject.replaceService(
      BuildContentManager::class.java,
      BuildContentManagerImpl(myProject), fixtureDisposable)
    syncViewManager = TestSyncViewManager(myProject)
    myProject.replaceService(SyncViewManager::class.java, syncViewManager, fixtureDisposable)
    buildViewManager = TestBuildViewManager(myProject)
    myProject.replaceService(BuildViewManager::class.java, buildViewManager, fixtureDisposable)
  }

  @Throws(Exception::class)
  override fun tearDown(): Unit = RunAll(
    ThrowableRunnable { if (::syncViewManager.isInitialized) syncViewManager.waitForPendingBuilds() },
    ThrowableRunnable { if (::buildViewManager.isInitialized) buildViewManager.waitForPendingBuilds() },
    ThrowableRunnable { runInEdtAndWait { Disposer.dispose(fixtureDisposable) } }
  ).run()

  fun assertSyncViewTree(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    assertExecutionTree(syncViewManager, assert)
  }

  fun assertSyncViewTreeEquals(executionTreeText: String) {
    assertExecutionTree(syncViewManager, executionTreeText, false)
  }

  fun assertSyncViewTreeSame(executionTreeText: String) {
    assertExecutionTree(syncViewManager, executionTreeText, true)
  }

  fun assertSyncViewTreeEquals(treeTestPresentationChecker: (String?) -> Unit) {
    assertExecutionTree(syncViewManager, treeTestPresentationChecker)
  }

  fun assertBuildViewTree(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    assertExecutionTree(buildViewManager, assert)
  }

  fun assertBuildViewTreeEquals(executionTree: String) {
    assertExecutionTree(buildViewManager, executionTree, false)
  }

  fun assertBuildViewTreeSame(executionTree: String) {
    assertExecutionTree(buildViewManager, executionTree, true)
  }

  fun assertBuildViewTreeEquals(treeTestPresentationChecker: (String?) -> Unit) {
    assertExecutionTree(buildViewManager, treeTestPresentationChecker)
  }

  fun assertSyncViewNode(nodeText: String, consoleText: String) {
    assertSyncViewNode(nodeText) { assertEquals(consoleText, it) }
  }

  fun assertSyncViewSelectedNode(nodeText: String, consoleText: String) {
    assertSyncViewSelectedNode(nodeText) { assertEquals(consoleText, it) }
  }

  fun assertSyncViewNode(nodeText: String, consoleTextChecker: (String) -> Unit) {
    assertExecutionTreeNode(syncViewManager, nodeText, { consoleTextChecker(it!!) }, null, false)
  }

  fun assertSyncViewSelectedNode(nodeText: String, consoleTextChecker: (String) -> Unit) {
    assertExecutionTreeNode(syncViewManager, nodeText, { consoleTextChecker(it!!) }, null, true)
  }

  fun getSyncViewRerunActions(): List<AnAction> {
    val buildView = syncViewManager.buildsMap[syncViewManager.getRecentBuild()]!!
    return buildView.restartActions
  }

  fun assertBuildViewNode(nodeText: String, consoleText: String) {
    assertBuildViewNode(nodeText) { assertEquals(consoleText, it) }
  }

  fun assertBuildViewSelectedNode(nodeText: String, consoleText: String) {
    assertBuildViewSelectedNode(nodeText) { assertEquals(consoleText, it) }
  }

  fun assertBuildViewNode(nodeText: String, consoleTextChecker: (String) -> Unit) {
    assertExecutionTreeNode(buildViewManager, nodeText, { consoleTextChecker(it!!) }, null, false)
  }

  fun assertBuildViewSelectedNode(nodeText: String, consoleTextChecker: (String) -> Unit) {
    assertExecutionTreeNode(buildViewManager, nodeText, { consoleTextChecker(it!!) }, null, true)
  }

  fun assertBuildViewNodeConsole(nodeText: String, consoleChecker: ((ExecutionConsole?) -> Unit)?) {
    assertExecutionTreeNode(buildViewManager, nodeText, null, consoleChecker, false)
  }

  fun assertBuildViewSelectedNodeConsole(nodeText: String, consoleChecker: ((ExecutionConsole?) -> Unit)?) {
    assertExecutionTreeNode(buildViewManager, nodeText, null, consoleChecker, true)
  }

  private fun assertExecutionTree(viewManager: TestViewManager, expected: String, ignoreTasksOrder: Boolean) {
    viewManager.waitForPendingBuilds()
    val recentBuild = viewManager.getRecentBuild()
    val buildView = viewManager.getBuildsMap()[recentBuild]
    assertExecutionTree(buildView!!, expected, ignoreTasksOrder)
  }

  @JvmName("assertSimpleExecutionTree")
  private fun assertExecutionTree(viewManager: TestViewManager, assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    assertExecutionTree(viewManager) { treeString ->
      val actualTree = buildTree(treeString!!)
      SimpleTreeAssertion.assertTree(actualTree) {
        assertNode("", assert = assert)
      }
    }
  }

  private fun assertExecutionTree(viewManager: TestViewManager, treeTestPresentationChecker: (String?) -> Unit) {
    viewManager.waitForPendingBuilds()
    val recentBuild = viewManager.getRecentBuild()
    val buildView = viewManager.getBuildsMap()[recentBuild]
    assertExecutionTree(buildView!!, treeTestPresentationChecker)
  }

  private fun assertExecutionTreeNode(
    viewManager: TestViewManager,
    nodeText: String,
    consoleTextChecker: ((String?) -> Unit)?,
    consoleChecker: ((ExecutionConsole?) -> Unit)?,
    assertSelected: Boolean
  ) {
    viewManager.waitForPendingBuilds()
    val recentBuild = viewManager.getRecentBuild()
    val buildView = viewManager.getBuildsMap()[recentBuild]
    assertExecutionTreeNode(buildView!!, nodeText, consoleTextChecker, consoleChecker, assertSelected)
  }

  companion object {
    fun assertExecutionTree(buildView: BuildView, expected: String, ignoreTasksOrder: Boolean) {
      val treeStringPresentation = getTreeStringPresentation(buildView)
      if (ignoreTasksOrder) {
        assertSameElements(
          buildTasksNodesAsList(
            treeStringPresentation.trim()),
          buildTasksNodesAsList(expected.trim())
        )
      }
      else {
        assertEquals(expected.trim(), treeStringPresentation.trim())
      }
    }

    fun assertExecutionTree(buildView: BuildView, treeTestPresentationChecker: (String?) -> Unit) {
      val treeStringPresentation = getTreeStringPresentation(buildView)
      treeTestPresentationChecker.invoke(treeStringPresentation)
    }

    fun assertExecutionTreeNode(
      buildView: BuildView,
      nodeText: String,
      consoleTextChecker: ((String?) -> Unit)?,
      consoleChecker: ((ExecutionConsole?) -> Unit)?,
      assertSelected: Boolean
    ) {
      val eventView = buildView.getView(BuildTreeConsoleView::class.java.name, BuildTreeConsoleView::class.java)
      eventView!!.addFilter { true }
      val tree = eventView.tree
      val node = runInEdtAndGet {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        PlatformTestUtil.waitWhileBusy(tree)

        TreeUtil.findNode(tree.model.root as DefaultMutableTreeNode) {
          val userObject = it.userObject
          userObject is ExecutionNode && userObject.name == nodeText
        }
      }
      val selectedPathComponent =
        if (!assertSelected && node != tree.selectionPath?.lastPathComponent) {
          runInEdtAndGet {
            TreeUtil.selectNode(tree, node)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            PlatformTestUtil.waitWhileBusy(tree)
            tree.selectionPath!!.lastPathComponent
          }
        }
        else {
          tree.selectionPath!!.lastPathComponent
        }
      if (node != selectedPathComponent) {
        assertEquals(node.toString(), selectedPathComponent.toString())
      }
      val selectedNodeConsole = runInEdtAndGet { eventView.selectedNodeConsole }
      consoleTextChecker?.invoke((selectedNodeConsole as? ConsoleViewImpl)?.text)
      consoleChecker?.invoke(selectedNodeConsole)
    }

    private fun getTreeStringPresentation(buildView: BuildView): @NotNull String {
      val eventView = buildView.getView(BuildTreeConsoleView::class.java.name, BuildTreeConsoleView::class.java)
      eventView!!.addFilter { true }
      return runInEdtAndGet {
        val tree = eventView.tree
        TreeUtil.expandAll(tree)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        PlatformTestUtil.waitWhileBusy(tree)
        return@runInEdtAndGet PlatformTestUtil.print(tree, false)
      }
    }

    private fun buildTasksNodesAsList(treeStringPresentation: String): List<String> {
      val list = mutableListOf<String>()
      val buffer = StringBuilder()
      for (line in treeStringPresentation.lineSequence()) {
        if (line.startsWith(" -") || line.startsWith("  :") || line.startsWith("  -")) {
          list.add(buffer.toString())
          buffer.clear()
        }
        buffer.appendLine(line)
      }
      if (buffer.isNotEmpty()) {
        list.add(buffer.toString())
      }
      return list
    }
  }

  private interface TestViewManager : ViewManager {
    fun getBuildsMap(): MutableMap<BuildDescriptor, BuildView>
    fun waitForPendingBuilds()
    fun getRecentBuild(): BuildDescriptor
  }

  private class TestSyncViewManager(project: Project) : SyncViewManager(project), TestViewManager {
    private val semaphore = Semaphore()
    private lateinit var recentBuild: BuildDescriptor
    override fun waitForPendingBuilds() = TestCase.assertTrue(semaphore.waitFor(1000))
    override fun getRecentBuild(): BuildDescriptor = recentBuild
    override fun getBuildsMap(): MutableMap<BuildDescriptor, BuildView> = super.getBuildsMap()

    override fun onBuildStart(buildDescriptor: BuildDescriptor) {
      super.onBuildStart(buildDescriptor)
      recentBuild = buildDescriptor
      semaphore.down()
    }

    override fun onBuildFinish(buildDescriptor: BuildDescriptor) {
      super.onBuildFinish(buildDescriptor)
      semaphore.up()
    }
  }

  private class TestBuildViewManager(project: Project) : BuildViewManager(project), TestViewManager {
    private val semaphore = Semaphore()
    private lateinit var recentBuild: BuildDescriptor
    override fun waitForPendingBuilds() {
      TestCase.assertTrue(semaphore.waitFor(2000))
      runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    }

    override fun getRecentBuild(): BuildDescriptor = recentBuild
    override fun getBuildsMap(): MutableMap<BuildDescriptor, BuildView> = super.getBuildsMap()
    override fun onBuildStart(buildDescriptor: BuildDescriptor) {
      super.onBuildStart(buildDescriptor)
      recentBuild = buildDescriptor
      semaphore.down()
    }

    override fun onBuildFinish(buildDescriptor: BuildDescriptor?) {
      super.onBuildFinish(buildDescriptor)
      semaphore.up()
    }
  }
}
