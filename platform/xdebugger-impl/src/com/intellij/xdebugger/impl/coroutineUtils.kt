// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import fleet.kernel.withEntities
import kotlinx.coroutines.flow.MutableStateFlow
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
