// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleOperationTestUtil")

package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.build.BuildProgressListener
import com.intellij.build.output.BuildOutputParser
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputDispatcherFactory
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputMessageDispatcher
import com.intellij.openapi.observable.operation.OperationExecutionContext
import com.intellij.openapi.observable.operation.OperationExecutionId
import com.intellij.openapi.observable.operation.OperationExecutionStatus
import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import com.intellij.openapi.observable.operation.core.ObservableOperationTrace
import com.intellij.openapi.observable.operation.core.awaitOperation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.use
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.observable.operation.core.waitForOperationAndPumpEdt
import org.jetbrains.plugins.gradle.execution.build.output.GradleOutputDispatcherFactory
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.getGradleTaskExecutionOperation
import org.jetbrains.plugins.gradle.util.getGradleProjectReloadOperation
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


fun <R> waitForGradleProjectReload(externalProjectPath: String, action: ThrowableComputable<R, Throwable>): R {
  return Disposer.newDisposable("waitForGradleProjectReload").use { disposable ->
    getGradleProjectReloadOperation(externalProjectPath, disposable)
      .waitForOperationAndPumpEdt(10.seconds, 10.minutes, action = action)
  }
}

fun <R> waitForAnyGradleProjectReload(action: ThrowableComputable<R, Throwable>): R {
  return Disposer.newDisposable("waitForAnyGradleProjectReload").use { disposable ->
    getGradleProjectReloadOperation(disposable)
      .waitForOperationAndPumpEdt(10.seconds, 10.minutes, action = action)
  }
}

fun <R> waitForAnyGradleTaskExecution(action: ThrowableComputable<R, Throwable>): R {
  return Disposer.newDisposable("waitForAnyGradleTaskExecution").use { disposable ->
    getGradleTaskExecutionOperation(disposable)
      .waitForOperationAndPumpEdt(10.seconds, 10.minutes, action = action)
  }
}

suspend fun <R> awaitAnyGradleProjectReload(action: suspend () -> R): R {
  return Disposer.newDisposable("awaitAnyGradleProjectReload").use { disposable ->
    getGradleProjectReloadOperation(disposable)
      .awaitOperation(10.seconds, 10.minutes, action = action)
  }
}

fun <R> waitForGradleEventDispatcherClosing(action: () -> R): R {
  return Disposer.newDisposable("waitForGradleEventDispatcherClosing").use { disposable ->
    getGradleEventDispatcherOperation(disposable)
      .waitForOperationAndPumpEdt(10.seconds, 10.minutes, action)
  }
}

fun <R> waitForAnyExecution(project: Project, action: () -> R): R {
  return Disposer.newDisposable("waitForAnyExecution").use { disposable ->
    getExecutionOperation(project, disposable)
      .waitForOperationAndPumpEdt(10.seconds, 10.minutes, action)
  }
}

/**
 * Operation which starts when Gradle accepted for receiving progress events in run, debug and test views,
 * and finishes when Gradle stopped these events.
 */
private fun getGradleEventDispatcherOperation(parentDisposable: Disposable): ObservableOperationTrace {
  val eventDispatcherOperation = AtomicOperationTrace("Gradle Event Dispatcher Lifecycle")
  val dispatcherFactory = object : ExternalSystemOutputDispatcherFactory {

    override val externalSystemId = GradleConstants.SYSTEM_ID

    override fun create(
      buildId: Any,
      buildProgressListener: BuildProgressListener,
      appendOutputToMainConsole: Boolean,
      parsers: List<BuildOutputParser>
    ): ExternalSystemOutputMessageDispatcher {
      eventDispatcherOperation.traceStart()
      val gradleDispatcher = GradleOutputDispatcherFactory()
        .create(buildId, buildProgressListener, appendOutputToMainConsole, parsers)
      return object : ExternalSystemOutputMessageDispatcher by gradleDispatcher {
        override fun close() {
          gradleDispatcher.invokeOnCompletion {
            eventDispatcherOperation.traceFinish()
          }
          gradleDispatcher.close()
        }
      }
    }
  }
  ExtensionTestUtil.maskExtensions(ExternalSystemOutputDispatcherFactory.EP_NAME, listOf(dispatcherFactory), parentDisposable)
  return eventDispatcherOperation
}

private val EXECUTOR_ID_KEY = OperationExecutionContext.createKey<String>("EXECUTOR_ID")

/**
 * General execution operation which wrapped by events from [ExecutionManager.EXECUTION_TOPIC].
 */
fun getExecutionOperation(project: Project, parentDisposable: Disposable): ObservableOperationTrace {
  val executionOperation = AtomicOperationTrace("General Process Execution")
  val ids = ConcurrentHashMap<String, OperationExecutionId>()
  val executionListener = object : ExecutionListener {

    override fun processStartScheduled(executorId: String, env: ExecutionEnvironment) {
      val executionId = ids.getOrPut(executorId) {
        OperationExecutionId.createId {
          putData(EXECUTOR_ID_KEY, executorId)
        }
      }
      executionOperation.traceSchedule(executionId)
    }

    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
      val executionId = ids.getOrPut(executorId) {
        OperationExecutionId.createId {
          putData(EXECUTOR_ID_KEY, executorId)
        }
      }
      executionOperation.traceStart(executionId)
    }

    override fun processNotStarted(executorId: String, env: ExecutionEnvironment, cause: Throwable?) {
      val executionId = ids.remove(executorId) ?: return
      val status = when (cause) {
        null -> OperationExecutionStatus.Failure()
        else -> OperationExecutionStatus.Failure(cause)
      }
      executionOperation.traceFinish(executionId, status)
    }

    override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
      val executionId = ids.remove(executorId) ?: return
      executionOperation.traceFinish(executionId)
    }
  }
  project.messageBus.connect(parentDisposable)
    .subscribe(ExecutionManager.EXECUTION_TOPIC, executionListener)
  return executionOperation
}