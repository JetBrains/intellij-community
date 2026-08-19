// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.diagnostic.PlatformMemoryUtil
import com.intellij.diagnostic.PlatformMemoryUtil.Companion.getInstance
import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.wm.impl.status.MemoryUsagePanel.calculateMemoryUsage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.function.Consumer

internal class MemoryUsagePanelScheduler(private val edtUpdateState: Consumer<MemoryData>) {
  @Suppress("RAW_SCOPE_CREATION")
  private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val signalFlow = MutableSharedFlow<Unit>(replay = 1)

  fun start() {
    coroutineScope.coroutineContext.cancelChildren()

    coroutineScope.launch {
      delay(Duration.ofSeconds(1))

      while (true) {
        signalFlow.emit(Unit)

        delay(Duration.ofSeconds(5))
      }
    }

    coroutineScope.launch {
      signalFlow.collect {
        try {
          val memoryData = getMemoryData()

          withContext(Dispatchers.UI) {
            edtUpdateState.accept(memoryData)
          }
        }
        catch (t: Throwable) {
          rethrowControlFlowException(t)
          logger<MemoryUsagePanelScheduler>().warn(t)
        }
      }
    }
  }

  private fun getMemoryData(): MemoryData {
    val appMemory = calculateMemoryUsage()
    val stats = getInstance().getCurrentProcessMemoryStats()

    val runtime = Runtime.getRuntime()
    val allocatedMem = runtime.totalMemory()
    val runtimeMemory = MemoryUsagePanel.MemoryStats(
      allocatedMem - runtime.freeMemory(),
      allocatedMem,
      runtime.maxMemory(),
    )
    return MemoryData(appMemory, runtimeMemory, stats)
  }

  fun request() {
    signalFlow.tryEmit(Unit)
  }

  fun stop() {
    coroutineScope.coroutineContext.cancelChildren()
  }

  fun dispose() {
    coroutineScope.cancel()
  }
}

internal class MemoryData(
  val appMemory: MemoryUsagePanel.AppMemoryUsage,
  val runtimeMemory: MemoryUsagePanel.MemoryStats,
  val processMemoryStats: PlatformMemoryUtil.MemoryStats?,
)