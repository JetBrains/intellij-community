// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.hotswap

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.impl.hotswap.HotSwapStatistics
import com.intellij.xdebugger.impl.hotswap.HotSwapVisibleStatus
import com.intellij.xdebugger.impl.hotswap.NOTIFICATION_TIME_SECONDS
import com.intellij.xdebugger.impl.rpc.XDebugHotSwapCurrentSessionStatus
import com.intellij.xdebugger.impl.rpc.XDebugHotSwapSessionId
import com.intellij.xdebugger.impl.rpc.XDebuggerHotSwapApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@Service(Service.Level.PROJECT)
internal class FrontendHotSwapManager(private val project: Project, val coroutineScope: CoroutineScope) {
  private val frontendStatusFlow = MutableStateFlow(null as XDebugHotSwapCurrentSessionStatus?).also { flow ->
    coroutineScope.launch {
      XDebuggerHotSwapApi.getInstance().currentSessionStatus(project.projectId()).collectLatest { state ->
        val newStatus = state
        flow.value = newStatus
        // clear success status after delay
        if (newStatus != null && newStatus.status == HotSwapVisibleStatus.SUCCESS) {
          launch(Dispatchers.Default) {
            delay(NOTIFICATION_TIME_SECONDS.seconds)
            flow.compareAndSet(newStatus, newStatus.copy(status = HotSwapVisibleStatus.NO_CHANGES))
          }
        }
      }
    }
  }

  val currentStatusFlow: StateFlow<XDebugHotSwapCurrentSessionStatus?> get() = frontendStatusFlow

  val currentStatus: XDebugHotSwapCurrentSessionStatus? get() = frontendStatusFlow.value

  fun performHotSwap(sessionId: XDebugHotSwapSessionId, source: HotSwapStatistics.HotSwapSource) {
    coroutineScope.launch {
      XDebuggerHotSwapApi.getInstance().performHotSwap(sessionId, source)
    }
  }

  fun notifyHidden() {
    // Hide locally
    frontendStatusFlow.value = null
    coroutineScope.launch {
      XDebuggerHotSwapApi.getInstance().hide(project.projectId())
    }
  }

  companion object {
    fun getInstance(project: Project): FrontendHotSwapManager = project.service()
  }
}
