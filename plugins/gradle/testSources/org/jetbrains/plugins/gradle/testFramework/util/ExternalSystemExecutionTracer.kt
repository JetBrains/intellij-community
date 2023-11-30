// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.build.events.MessageEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.observable.operation.OperationExecutionStatus
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import org.jetbrains.plugins.gradle.util.whenExternalSystemEventReceived
import org.jetbrains.plugins.gradle.util.whenExternalSystemTaskFinished
import org.jetbrains.plugins.gradle.util.whenExternalSystemTaskOutputAdded
import org.junit.jupiter.api.Assertions

class ExternalSystemExecutionTracer {

  var status: OperationExecutionStatus? = null

  val stdout: MutableList<String> = ArrayList()
  val stderr: MutableList<String> = ArrayList()
  val messages: MutableList<MessageEvent> = ArrayList()

  inline fun <R> traceExecution(action: () -> R): R {
    return Disposer.newDisposable().use { disposable ->
      install(disposable)
      action()
    }
  }

  fun install(parentDisposable: Disposable) {
    whenExternalSystemTaskOutputAdded(parentDisposable) { _, text, stdOut ->
      when (stdOut) {
        true -> stdout.add(text)
        else -> stderr.add(text)
      }
    }
    whenExternalSystemEventReceived(parentDisposable) { event ->
      if (event is ExternalSystemBuildEvent) {
        val buildEvent = event.buildEvent
        if (buildEvent is MessageEvent) {
          messages.add(buildEvent)
        }
      }

    }
    whenExternalSystemTaskFinished(parentDisposable) { _, status ->
      this.status = status
    }
  }

  fun printExecutionOutput() {
    println("STDOUT START")
    stdout.forEach { print(it) }
    println("STDOUT END")
    println("STDERR START")
    stderr.forEach { print(it) }
    println("STDERR END")
    println("MESSAGES START")
    messages.forEach {
      println(it.kind.name + ": " + it.message)
      println(it.result?.details)
    }
    println("MESSAGES END")

  }

  companion object {

    inline fun <R> assertExecutionStatusIsSuccess(action: () -> R): R {
      val tracer = ExternalSystemExecutionTracer()
      val result = tracer.traceExecution {
        action()
      }
      val status = tracer.status
      if (status != OperationExecutionStatus.Success) {
        tracer.printExecutionOutput()
      }
      if (status is OperationExecutionStatus.Failure) {
        throw AssertionError("Execution failed, but shouldn't", status.cause)
      }
      Assertions.assertEquals(OperationExecutionStatus.Success, status)
      return result
    }

    inline fun <R> printExecutionOutputOnException(action: () -> R): R {
      val tracer = ExternalSystemExecutionTracer()
      try {
        return tracer.traceExecution {
          action()
        }
      }
      catch (ex: Throwable) {
        tracer.printExecutionOutput()
        throw ex
      }
    }

    inline fun <R> printExecutionOutput(action: () -> R): R {
      val tracer = ExternalSystemExecutionTracer()
      try {
        return tracer.traceExecution {
          action()
        }
      }
      finally {
        tracer.printExecutionOutput()
      }
    }
  }
}