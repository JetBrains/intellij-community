// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixture

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.tracker.OperationLeakTracker
import org.jetbrains.plugins.gradle.testFramework.util.getExecutionOperation
import org.junit.jupiter.api.Assertions

class GradleExecutionEnvironmentFixture(
  private val project: Project
) : IdeaTestFixture {

  private lateinit var testDisposable: Disposable

  private lateinit var executionLeakTracker: OperationLeakTracker

  private var executionEnvironment: ExecutionEnvironment? = null

  override fun setUp() {
    executionEnvironment = null

    testDisposable = Disposer.newDisposable()

    executionLeakTracker = OperationLeakTracker { getExecutionOperation(project, it) }
    executionLeakTracker.setUp()

    installExecutionListener()
  }

  override fun tearDown() {
    runAll(
      { executionLeakTracker.tearDown() },
      { Disposer.dispose(testDisposable) }
    )
  }

  private fun installExecutionListener() {
    val executionListener = object : ExecutionListener {

      override fun processStartScheduled(executorId: String, env: ExecutionEnvironment) {
        executionEnvironment = null
      }

      override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
        executionEnvironment = env
      }
    }
    project.messageBus.connect(testDisposable)
      .subscribe(ExecutionManager.EXECUTION_TOPIC, executionListener)
  }

  fun getExecutionEnvironment(): ExecutionEnvironment {
    Assertions.assertNotNull(executionEnvironment) {
      "Gradle execution isn't started."
    }
    return executionEnvironment!!
  }

  fun <R> assertExecutionEnvironmentIsReady(action: () -> R): R {
    return executionLeakTracker.withAllowedOperation(1, action)
  }

  suspend fun <R> assertExecutionEnvironmentIsReadyAsync(action: suspend () -> R): R {
    return executionLeakTracker.withAllowedOperationAsync(1, action)
  }
}