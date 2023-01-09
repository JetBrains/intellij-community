// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner

import com.intellij.execution.executors.DefaultRunExecutor.EXECUTOR_ID
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ExecutionDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode.NO_PROGRESS_SYNC
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.TestActionEvent
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.gradle.action.GradleRerunFailedTestsAction
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.jetbrains.plugins.gradle.util.waitForTaskExecution
import java.io.File

abstract class GradleRerunFailedTestsTestCase : GradleImportingTestCase() {

  private lateinit var testDisposable: Disposable
  private lateinit var testExecutionEnvironment: ExecutionEnvironment
  private lateinit var testExecutionConsole: GradleTestsExecutionConsole

  override fun setUp() {
    super.setUp()
    testDisposable = Disposer.newDisposable()
    initExecutionConsoleHandler()
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { Disposer.dispose(testDisposable) },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }

  /**
   * Call this method inside [setUp] to print events trace to console
   */
  @Suppress("unused")
  private fun initTextNotificationEventsPrinter() {
    val notificationManager = ExternalSystemProgressNotificationManager.getInstance()
    notificationManager.addNotificationListener(object : ExternalSystemTaskNotificationListenerAdapter() {
      override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
        if (id.type == ExternalSystemTaskType.EXECUTE_TASK) {
          when (stdOut) {
            true -> print(text)
            else -> System.err.print(text)
          }
        }
      }
    }, testDisposable)
  }

  private fun initExecutionConsoleHandler() {
    ExtensionTestUtil.maskExtensions(ExternalSystemExecutionConsoleManager.EP_NAME, listOf(object : GradleTestsExecutionConsoleManager() {
      override fun attachExecutionConsole(
        project: Project,
        task: ExternalSystemTask,
        env: ExecutionEnvironment?,
        processHandler: ProcessHandler?
      ) = super.attachExecutionConsole(project, task, env, processHandler).also {
        testExecutionEnvironment = env!!
        testExecutionConsole = it!!
      }
    }), testDisposable)
  }

  private fun getTestsExecutionTree() = invokeAndWaitIfNeeded {
    val tree = testExecutionConsole.resultsViewer.treeView!!
    TestConsoleProperties.HIDE_PASSED_TESTS.set(testExecutionConsole.properties, false)
    PlatformTestUtil.expandAll(tree)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    PlatformTestUtil.waitWhileBusy(tree)
    PlatformTestUtil.print(tree, false)
  }

  fun getJUnitTestsExecutionTree(): String {
    val flattenTree = getTestsExecutionTree()
      // removes trailing () for test methods in junit 5
      .replace("()", "")
      .split("\n")
      // removes package for tests classes in junit 4
      .map { if (it.trim().startsWith("-")) it.substringBefore("-") + "-" + it.substringAfter("-").split(".").last() else it }
      .toMutableList()
    partitionLeaves(flattenTree)
      .map { flattenTree.subList(it.first, it.last + 1) }
      .forEach { it.sortWith(NaturalComparator.INSTANCE) }
    return flattenTree.joinToString("\n")
  }

  private fun partitionLeaves(flattenTree: List<String>) = sequence {
    var left = -1
    for ((i, node) in flattenTree.withIndex()) {
      val isLeaf = !node.trim().startsWith("-")
      if (isLeaf && left == -1) {
        left = i
      }
      else if (!isLeaf && left != -1) {
        yield(left until i)
        left = -1
      }
    }
    if (left != -1) {
      yield(left until flattenTree.size)
    }
  }

  fun execute(tasksAndArguments: String, parameters: String? = null) {
    val settings = ExternalSystemTaskExecutionSettings().apply {
      externalProjectPath = projectPath
      taskNames = tasksAndArguments.split(" ")
      scriptParameters = parameters
      externalSystemIdString = SYSTEM_ID.id
    }
    ExternalSystemUtil.runTask(settings, EXECUTOR_ID, myProject, SYSTEM_ID, null, NO_PROGRESS_SYNC)
  }

  fun performRerunFailedTestsAction(): Boolean = invokeAndWaitIfNeeded {
    val rerunAction = GradleRerunFailedTestsAction(testExecutionConsole)
    rerunAction.setModelProvider { testExecutionConsole.resultsViewer }
    val actionEvent = TestActionEvent.createTestEvent(
      SimpleDataContext.builder()
        .add(ExecutionDataKeys.EXECUTION_ENVIRONMENT, testExecutionEnvironment)
        .add(CommonDataKeys.PROJECT, myProject)
        .build())
    rerunAction.update(actionEvent)
    if (actionEvent.presentation.isEnabled) {
      waitForTaskExecution {
        rerunAction.actionPerformed(actionEvent)
      }
    }
    actionEvent.presentation.isEnabled
  }

  fun VirtualFile.replaceFirst(old: String, new: String) =
    updateIoFile { writeText(readText().replaceFirst(old, new)) }

  private fun VirtualFile.updateIoFile(action: File.() -> Unit) {
    File(path).apply(action)
    refreshIoFiles(path)
  }

  private fun refreshIoFiles(vararg paths: String) {
    val localFileSystem = LocalFileSystem.getInstance()
    localFileSystem.refreshIoFiles(paths.map { File(it) }, false, true, null)
  }
}