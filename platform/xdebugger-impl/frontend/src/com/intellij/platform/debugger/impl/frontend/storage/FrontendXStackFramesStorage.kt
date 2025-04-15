// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.storage

import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.frame.FrontendXStackFrame
import com.intellij.xdebugger.impl.rpc.XStackFrameDto
import com.intellij.xdebugger.impl.rpc.XStackFrameId
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class FrontendXStackFramesStorage : AbstractCoroutineContextElement(FrontendXStackFramesStorage) {
  companion object Key : CoroutineContext.Key<FrontendXStackFramesStorage>

  private val cache = ConcurrentHashMap<XStackFrameId, FrontendXStackFrame>()

  fun computeIfAbsent(key: XStackFrameId, compute: (XStackFrameId) -> FrontendXStackFrame): FrontendXStackFrame {
    return cache.computeIfAbsent(key, compute)
  }
}

internal fun CoroutineScope.getOrCreateStack(frameDto: XStackFrameDto, project: Project): FrontendXStackFrame {
  val storageCache = coroutineContext[FrontendXStackFramesStorage]
  requireNotNull(storageCache) { "StacksStorage not found" }

  return storageCache.computeIfAbsent(frameDto.stackFrameId) {
    FrontendXStackFrame(frameDto, project, this)
  }
}
