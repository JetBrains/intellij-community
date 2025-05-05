// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures

import com.intellij.build.*
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.assertion.BuildViewAssertions
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.concurrency.Semaphore
import junit.framework.TestCase
import org.junit.jupiter.api.Assertions.assertEquals

class BuildViewTestFixture(
  private val project: Project,
) : IdeaTestFixture {

  private lateinit var fixtureDisposable: Disposable

  private lateinit var syncViewManager: TestSyncViewManager
  private lateinit var buildViewManager: TestBuildViewManager

  private val syncView: BuildView get() = syncViewManager.getBuildView()
  private val buildView: BuildView get() = buildViewManager.getBuildView()

  @Throws(Exception::class)
  override fun setUp() {
    fixtureDisposable = Disposer.newDisposable()

    project.replaceService(BuildContentManager::class.java, BuildContentManagerImpl(project), fixtureDisposable)
    syncViewManager = TestSyncViewManager(project)
    project.replaceService(SyncViewManager::class.java, syncViewManager, fixtureDisposable)
    buildViewManager = TestBuildViewManager(project)
    project.replaceService(BuildViewManager::class.java, buildViewManager, fixtureDisposable)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    runAll(
      { if (::syncViewManager.isInitialized) syncViewManager.waitForPendingBuilds() },
      { if (::buildViewManager.isInitialized) buildViewManager.waitForPendingBuilds() },
      { runInEdtAndWait { Disposer.dispose(fixtureDisposable) } }
    )
  }

  fun getSyncViewRerunActions(): List<AnAction> = syncView.restartActions

  fun assertSyncViewTree(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    BuildViewAssertions.assertBuildViewTree(syncView, assert)
  }

  fun assertSyncViewTreeEquals(executionTreeText: String) {
    BuildViewAssertions.assertBuildViewTreeText(syncView) {
      assertEquals(executionTreeText.trim(), it.trim())
    }
  }

  fun assertSyncViewTreeSame(executionTreeText: String) {
    BuildViewAssertions.assertBuildViewTreeText(syncView) {
      CollectionAssertions.assertEqualsUnordered(buildTasksNodesAsList(it.trim()), buildTasksNodesAsList(executionTreeText.trim()))
    }
  }

  fun assertSyncViewTreeEquals(treeTestPresentationChecker: (String) -> Unit) {
    BuildViewAssertions.assertBuildViewTreeText(syncView, treeTestPresentationChecker)
  }

  fun assertSyncViewNode(nodeText: String, consoleText: String) {
    BuildViewAssertions.assertBuildViewNodeConsoleText(syncView, nodeText) {
      assertEquals(consoleText, it)
    }
  }

  fun assertSyncViewNode(nodeText: String, consoleTextChecker: (String) -> Unit) {
    BuildViewAssertions.assertBuildViewNodeConsoleText(syncView, nodeText, consoleTextChecker)
  }

  fun assertSyncViewSelectedNode(nodeText: String, consoleText: String) {
    BuildViewAssertions.assertBuildViewNodeIsSelected(syncView, nodeText)
    BuildViewAssertions.assertBuildViewNodeConsoleText(syncView, nodeText) {
      assertEquals(consoleText, it)
    }
  }

  fun assertSyncViewSelectedNode(nodeText: String, consoleTextChecker: (String) -> Unit) {
    BuildViewAssertions.assertBuildViewNodeIsSelected(syncView, nodeText)
    BuildViewAssertions.assertBuildViewNodeConsoleText(syncView, nodeText, consoleTextChecker)
  }

  fun assertBuildViewTree(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    BuildViewAssertions.assertBuildViewTree(buildView, assert)
  }

  fun assertBuildViewTreeEquals(executionTree: String) {
    BuildViewAssertions.assertBuildViewTreeText(buildView) {
      assertEquals(executionTree.trim(), it.trim())
    }
  }

  fun assertBuildViewTreeSame(executionTree: String) {
    BuildViewAssertions.assertBuildViewTreeText(buildView) {
      CollectionAssertions.assertEqualsUnordered(buildTasksNodesAsList(it.trim()), buildTasksNodesAsList(executionTree.trim()))
    }
  }

  fun assertBuildViewTreeEquals(treeTestPresentationChecker: (String?) -> Unit): Unit =
    BuildViewAssertions.assertBuildViewTreeText(buildView, treeTestPresentationChecker)

  fun assertBuildViewNode(nodeText: String, consoleText: String) {
    BuildViewAssertions.assertBuildViewNodeConsoleText(buildView, nodeText) {
      assertEquals(consoleText, it)
    }
  }

  fun assertBuildViewNode(nodeText: String, consoleTextChecker: (String) -> Unit) {
    BuildViewAssertions.assertBuildViewNodeConsoleText(buildView, nodeText, consoleTextChecker)
  }

  fun assertBuildViewNodeConsole(nodeText: String, consoleChecker: (ExecutionConsole) -> Unit) {
    BuildViewAssertions.assertBuildViewNodeConsole(buildView, nodeText, consoleChecker)
  }

  fun assertBuildViewSelectedNode(nodeText: String, consoleText: String) {
    BuildViewAssertions.assertBuildViewNodeIsSelected(buildView, nodeText)
    BuildViewAssertions.assertBuildViewNodeConsoleText(buildView, nodeText) {
      assertEquals(consoleText, it)
    }
  }

  fun assertBuildViewSelectedNode(nodeText: String, consoleTextChecker: (String) -> Unit) {
    BuildViewAssertions.assertBuildViewNodeIsSelected(buildView, nodeText)
    BuildViewAssertions.assertBuildViewNodeConsoleText(buildView, nodeText, consoleTextChecker)
  }

  fun assertBuildViewSelectedNodeConsole(nodeText: String, consoleChecker: (ExecutionConsole) -> Unit) {
    BuildViewAssertions.assertBuildViewNodeIsSelected(buildView, nodeText)
    BuildViewAssertions.assertBuildViewNodeConsole(buildView, nodeText, consoleChecker)
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

  private class TestSyncViewManager(project: Project) : SyncViewManager(project) {

    private val semaphore = Semaphore()

    private lateinit var recentBuild: BuildDescriptor

    fun getBuildView(): BuildView {
      waitForPendingBuilds()
      return buildsMap[recentBuild]!!
    }

    fun waitForPendingBuilds() {
      TestCase.assertTrue(semaphore.waitFor(1000))
      runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    }

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

  private class TestBuildViewManager(project: Project) : BuildViewManager(project) {

    private val semaphore = Semaphore()

    private lateinit var recentBuild: BuildDescriptor

    fun getBuildView(): BuildView {
      waitForPendingBuilds()
      return buildsMap[recentBuild]!!
    }

    fun waitForPendingBuilds() {
      TestCase.assertTrue(semaphore.waitFor(2000))
      runInEdtAndWait { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
    }

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
