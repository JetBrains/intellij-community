// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleOperationTestUtil")

package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.operation.OperationExecutionId
import com.intellij.openapi.observable.operation.OperationExecutionStatus
import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import com.intellij.openapi.observable.operation.core.ObservableOperationTrace
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * General execution operation which wrapped by events from [ExecutionManager.EXECUTION_TOPIC].
 */
fun getExecutionOperation(project: Project, parentDisposable: Disposable): ObservableOperationTrace {
  val executionOperation = AtomicOperationTrace("General Process Execution")
  val executionIds = ConcurrentHashMap<String, OperationExecutionId>()
  val executionListener = object : ExecutionListener {

    override fun processStartScheduled(executorId: String, env: ExecutionEnvironment) {
      val executionId = executionIds.getOrPut(executorId, OperationExecutionId::createId)
      executionOperation.traceSchedule(executionId)
    }

    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
      val executionId = executionIds.getOrPut(executorId, OperationExecutionId::createId)
      executionOperation.traceStart(executionId)
    }

    override fun processNotStarted(executorId: String, env: ExecutionEnvironment, cause: Throwable?) {
      val executionId = executionIds.remove(executorId) ?: return
      val status = when (cause) {
        null -> OperationExecutionStatus.Failure()
        else -> OperationExecutionStatus.Failure(cause)
      }
      executionOperation.traceStart(executionId)
      executionOperation.traceFinish(executionId, status)
    }

    override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
      val executionId = executionIds.remove(executorId) ?: return
      executionOperation.traceFinish(executionId)
    }
  }
  project.messageBus.connect(parentDisposable)
    .subscribe(ExecutionManager.EXECUTION_TOPIC, executionListener)
  return executionOperation
}