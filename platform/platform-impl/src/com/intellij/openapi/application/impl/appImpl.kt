// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import io.opentelemetry.api.metrics.BatchCallback
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.atomic.AtomicInteger

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