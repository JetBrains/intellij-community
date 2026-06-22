// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.hotswap

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.durableWithStateReset
import com.intellij.platform.debugger.impl.frontend.util.SequentialRpcRequestsExecutor
import com.intellij.platform.debugger.impl.rpc.HotSwapSource
import com.intellij.platform.debugger.impl.rpc.HotSwapVisibleStatus
import com.intellij.platform.debugger.impl.rpc.XDebugHotSwapCurrentSessionStatus
import com.intellij.platform.debugger.impl.rpc.XDebugHotSwapSessionId
import com.intellij.platform.debugger.impl.rpc.XDebuggerHotSwapApi
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.impl.hotswap.NOTIFICATION_TIME_SECONDS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
internal class FrontendHotSwapManager(private val project: Project, val coroutineScope: CoroutineScope) {
  private val sequentialExecutor = SequentialRpcRequestsExecutor.create(coroutineScope)
  val currentStatusFlow: StateFlow<XDebugHotSwapCurrentSessionStatus?>
    field = MutableStateFlow(null)

  init {
    coroutineScope.launch {
      durableWithStateReset(block = {
        val statusFlow = XDebuggerHotSwapApi.getInstance().currentSessionStatus(project.projectId())
        statusFlow.collectLatest { state ->
          currentStatusFlow.value = state
          // clear success status after delay
          if (state?.status == HotSwapVisibleStatus.Success) {
            delay(NOTIFICATION_TIME_SECONDS.seconds)
            currentStatusFlow.compareAndSet(state, state.copy(status = HotSwapVisibleStatus.NoChanges))
          }
        }
      }, stateReset = { currentStatusFlow.value = null })
    }
  }

  val currentStatus: XDebugHotSwapCurrentSessionStatus? get() = currentStatusFlow.value

  fun performHotSwap(sessionId: XDebugHotSwapSessionId, source: HotSwapSource) {
    sequentialExecutor.execute {
      XDebuggerHotSwapApi.getInstance().performHotSwap(sessionId, source)
    }
  }

  fun notifyHidden() {
    // Hide locally
    currentStatusFlow.value = null
    sequentialExecutor.execute {
      XDebuggerHotSwapApi.getInstance().hide(project.projectId())
    }
  }

  companion object {
    fun getInstance(project: Project): FrontendHotSwapManager = project.service()
  }
}
