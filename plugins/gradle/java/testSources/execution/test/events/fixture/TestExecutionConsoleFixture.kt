// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events.fixture

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixture
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsoleManager
import org.jetbrains.plugins.gradle.testFramework.util.tree.TreeAssertion
import org.jetbrains.plugins.gradle.testFramework.util.tree.buildTree
import org.jetbrains.plugins.gradle.testFramework.util.tree.sortedTree
import org.junit.jupiter.api.AssertionFailureBuilder
import org.junit.jupiter.api.Assertions

class TestExecutionConsoleFixture : IdeaTestFixture {

  private lateinit var testDisposable: Disposable
  private var testExecutionEnvironment: ExecutionEnvironment? = null
  private var testExecutionConsole: GradleTestsExecutionConsole? = null

  override fun setUp() {
    testDisposable = Disposer.newDisposable()
    initExecutionConsoleHandler()
  }

  override fun tearDown() {
    Disposer.dispose(testDisposable)
  }

  fun getTestExecutionEnvironment(): ExecutionEnvironment {
    return testExecutionEnvironment!!
  }

  fun getTestExecutionConsole(): GradleTestsExecutionConsole {
    return testExecutionConsole!!
  }

  private fun initExecutionConsoleHandler() {
    var _testExecutionEnvironment: ExecutionEnvironment? = null
    var _testExecutionConsole: GradleTestsExecutionConsole? = null
    val consoleManager = object : GradleTestsExecutionConsoleManager() {
      override fun attachExecutionConsole(
        project: Project,
        task: ExternalSystemTask,
        env: ExecutionEnvironment?,
        processHandler: ProcessHandler?
      ): GradleTestsExecutionConsole? {
        _testExecutionEnvironment = env!!
        _testExecutionConsole = super.attachExecutionConsole(project, task, env, processHandler)!!
        return _testExecutionConsole
      }
    }
    ExtensionTestUtil.maskExtensions(ExternalSystemExecutionConsoleManager.EP_NAME, listOf(consoleManager), testDisposable)
    val eventListener = object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        testExecutionEnvironment = _testExecutionEnvironment
        testExecutionConsole = _testExecutionConsole
        _testExecutionEnvironment = null
        _testExecutionConsole = null
      }
    }
    val notificationManager = ExternalSystemProgressNotificationManager.getInstance()
    notificationManager.addNotificationListener(eventListener, testDisposable)
  }

  private fun getTestExecutionTreeString(): String {
    return invokeAndWaitIfNeeded {
      val tree = getTestExecutionConsole().resultsViewer.treeView!!
        .also { PlatformTestUtil.waitWhileBusy(it) }
      TestConsoleProperties.HIDE_PASSED_TESTS.set(getTestExecutionConsole().properties, false)
        .also { PlatformTestUtil.waitWhileBusy(tree) }
      PlatformTestUtil.expandAll(tree)
        .also { PlatformTestUtil.waitWhileBusy(tree) }
      PlatformTestUtil.print(tree, false)
        .also { PlatformTestUtil.waitWhileBusy(tree) }
    }
  }

  private fun getTestExecutionConsoleString(): String {
    return invokeAndWaitIfNeeded {
      val tree = getTestExecutionConsole().resultsViewer.treeView!!
        .also { PlatformTestUtil.waitWhileBusy(it) }
      TestConsoleProperties.HIDE_PASSED_TESTS.set(getTestExecutionConsole().properties, false)
        .also { PlatformTestUtil.waitWhileBusy(tree) }
      PlatformTestUtil.expandAll(tree)
        .also { PlatformTestUtil.waitWhileBusy(tree) }
      TreeUtil.selectFirstNode(tree)
        .also { PlatformTestUtil.waitWhileBusy(tree) }
      val console = getTestExecutionConsole().console as ConsoleViewImpl
      console.flushDeferredText()
        .also { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
      console.text
    }
  }

  fun assertTestExecutionConsoleContains(expected: String) {
    val actual = getTestExecutionConsoleString()
    if (expected !in actual) {
      throw AssertionFailureBuilder.assertionFailure()
        .message("Test execution console doesn't contain")
        .expected(expected)
        .actual(actual)
        .build()
    }
  }

  fun assertTestExecutionTree(assert: TreeAssertion.Node<Nothing?>.() -> Unit) {
    val treeString = getTestExecutionTreeString()
    val tree = buildTree(treeString)
    TreeAssertion.assertTree(tree.sortedTree()) {
      assertNode("[root]", assert)
    }
  }

  fun assertTestExecutionTreeIsEmpty() {
    assertTestExecutionTree {}
  }

  fun assertTestExecutionTreeIsNotCreated() {
    Assertions.assertNull(testExecutionConsole) {
      "Test execution console shouldn't be created and shown"
    }
  }

  fun assertSMTestProxyTree(assert: TreeAssertion<AbstractTestProxy>.() -> Unit) {
    val resultsViewer = getTestExecutionConsole().resultsViewer
    val treeView = resultsViewer.treeView!!
    invokeAndWaitIfNeeded { PlatformTestUtil.waitWhileBusy(treeView) }
    val roots = resultsViewer.root.children
    val tree = buildTree(roots, { name }, { children })
    runReadAction { // all navigation tests requires read action
      TreeAssertion.assertTree(tree.sortedTree(), assert)
    }
  }
}