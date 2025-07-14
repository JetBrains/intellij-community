// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.hotswap

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.impl.hotswap.NOTIFICATION_TIME_SECONDS
import com.intellij.xdebugger.impl.rpc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@Service(Service.Level.PROJECT)
internal class FrontendHotSwapManager(private val project: Project, val coroutineScope: CoroutineScope) {
  private val frontendStatusFlow = MutableStateFlow(null as XDebugHotSwapCurrentSessionStatus?).also { flow ->
    coroutineScope.launch {
      XDebuggerHotSwapApi.getInstance().currentSessionStatus(project.projectId()).collectLatest { state ->
        flow.value = state
        // clear success status after delay
        if (state?.status == HotSwapVisibleStatus.SUCCESS) {
          delay(NOTIFICATION_TIME_SECONDS.seconds)
          flow.compareAndSet(state, state.copy(status = HotSwapVisibleStatus.NO_CHANGES))
        }
      }
    }
  }

  val currentStatusFlow: StateFlow<XDebugHotSwapCurrentSessionStatus?> get() = frontendStatusFlow

  val currentStatus: XDebugHotSwapCurrentSessionStatus? get() = frontendStatusFlow.value

  fun performHotSwap(sessionId: XDebugHotSwapSessionId, source: HotSwapSource) {
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
