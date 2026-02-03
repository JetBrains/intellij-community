// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionEnvironmentFixture
import org.junit.jupiter.api.Assertions

class GradleExecutionEnvironmentFixtureImpl(
  private val project: Project,
) : GradleExecutionEnvironmentFixture {

  private lateinit var testDisposable: Disposable

  private var executionEnvironment: ExecutionEnvironment? = null

  override fun setUp() {
    executionEnvironment = null

    testDisposable = Disposer.newDisposable()

    installExecutionListener()
  }

  override fun tearDown() {
    Disposer.dispose(testDisposable)
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

  override fun getExecutionEnvironment(): ExecutionEnvironment {
    Assertions.assertNotNull(executionEnvironment) {
      "Gradle execution isn't started."
    }
    return executionEnvironment!!
  }
}