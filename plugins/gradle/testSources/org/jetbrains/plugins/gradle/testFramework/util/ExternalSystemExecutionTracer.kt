// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.build.events.MessageEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import org.jetbrains.plugins.gradle.util.whenExternalSystemEventReceived
import org.jetbrains.plugins.gradle.util.whenExternalSystemTaskOutputAdded

class ExternalSystemExecutionTracer {

  val stdout: MutableList<String> = ArrayList()
  val stderr: MutableList<String> = ArrayList()
  val output: MutableList<String> = ArrayList()
  val messages: MutableList<MessageEvent> = ArrayList()

  inline fun <R> traceExecution(mode: PrintOutputMode = PrintOutputMode.NEVER, action: () -> R): R {
    try {
      return Disposer.newDisposable().use { disposable ->
        install(disposable)
        action()
      }
    }
    catch (ex: Throwable) {
      if (mode == PrintOutputMode.ON_EXCEPTION) {
        printExecutionOutput()
      }
      throw ex
    }
    finally {
      if (mode == PrintOutputMode.ALWAYS) {
        printExecutionOutput()
      }
    }
  }

  fun install(parentDisposable: Disposable) {
    whenExternalSystemTaskOutputAdded(parentDisposable) { _, text, stdOut ->
      output.add(text)
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

  enum class PrintOutputMode { NEVER, ALWAYS, ON_EXCEPTION }
}