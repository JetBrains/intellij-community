// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.CoroutineScope
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import fleet.kernel.withEntities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.annotations.ApiStatus

// Used only for Java code, since MutableStateFlow function cannot be called there.
internal fun <T> createMutableStateFlow(initialValue: T): MutableStateFlow<T> {
  return MutableStateFlow(initialValue)
}

@ApiStatus.Internal
suspend fun XDebugSessionImpl.id(): XDebugSessionId {
  val entity = entity.await()
  return withEntities(entity) {
    entity.sessionId
  }
}


// Used only for Java code, since combine cannot be called there
internal fun createSessionSuspendedFlow(
  cs: CoroutineScope,
  pausedFlow: StateFlow<Boolean>,
  suspendContextFlow: StateFlow<XSuspendContext?>,
): StateFlow<Boolean> {
  return combine(pausedFlow, suspendContextFlow) { paused, suspendContext ->
    paused && suspendContext != null
  }.stateIn(cs, SharingStarted.Eagerly, pausedFlow.value && suspendContextFlow.value != null)
}