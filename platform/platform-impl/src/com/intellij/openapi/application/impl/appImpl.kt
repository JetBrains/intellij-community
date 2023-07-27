// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import io.opentelemetry.api.metrics.BatchCallback
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

/** Count read & write actions executed, and report to OpenTelemetry Metrics  */
internal class OTelReadWriteActionsMonitor(meter: Meter) : AutoCloseable {
  private val batchCallback: BatchCallback
  private val readActionsExecuted = AtomicInteger()
  private val writeActionsExecuted = AtomicInteger()

  init {
    val raExecutionsCounter = meter.counterBuilder("ReadAction.executionsCount")
      .setDescription("Total read actions executed")
      .buildObserver()
    val waExecutionCounter = meter.counterBuilder("WriteAction.executionsCount")
      .setDescription("Total write actions executed")
      .buildObserver()
    batchCallback = meter.batchCallback(
      Runnable {
        raExecutionsCounter.record(readActionsExecuted.get().toLong())
        waExecutionCounter.record(writeActionsExecuted.get().toLong())
      },
      raExecutionsCounter,
      waExecutionCounter
    )
  }

  fun readActionExecuted() {
    readActionsExecuted.incrementAndGet()
  }

  fun writeActionExecuted() {
    writeActionsExecuted.incrementAndGet()
  }

  override fun close() {
    batchCallback.close()
  }
}

@ApiStatus.Internal
suspend fun exitAppOrExitProcess() {
  try {
    withTimeout(10.seconds) {
      withContext(Dispatchers.EDT) {
        ApplicationManagerEx.getApplicationEx().exit(ApplicationEx.FORCE_EXIT or ApplicationEx.EXIT_CONFIRMED)
      }
    }
  }
  catch (e: CancellationException) {
    exitProcess(0)
  }
  catch (e: Throwable) {
    logger<ApplicationManager>().warn(e)
  }
  finally {
    exitProcess(0)
  }
}