// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.storage

import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.frame.FrontendXStackFrame
import com.intellij.platform.debugger.impl.rpc.XStackFrameDto
import com.intellij.platform.debugger.impl.rpc.XStackFrameId
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class FrontendXStackFramesStorage : AbstractCoroutineContextElement(FrontendXStackFramesStorage) {
  companion object Key : CoroutineContext.Key<FrontendXStackFramesStorage>

  private val cache = ConcurrentHashMap<XStackFrameId, FrontendXStackFrame>()

  fun findStackFrame(id: XStackFrameId): FrontendXStackFrame? = cache[id]

  fun getOrCreateStackFrame(project: Project, scope: CoroutineScope, frameDto: XStackFrameDto): FrontendXStackFrame {
    return cache.computeIfAbsent(frameDto.stackFrameId) {
      with(frameDto) {
        FrontendXStackFrame(
          stackFrameId,
          project,
          scope,
          sourcePosition,
          backgroundColor,
          equalityObject,
          evaluator,
          captionInfo,
          textPresentation,
          canDrop,
        )
      }
    }
  }
}

internal fun CoroutineScope.getOrCreateStackFrame(frameDto: XStackFrameDto, project: Project): FrontendXStackFrame {
  val storageCache = coroutineContext[FrontendXStackFramesStorage]
  requireNotNull(storageCache) { "StacksStorage not found" }

  return storageCache.getOrCreateStackFrame(project, this, frameDto)
}

internal fun CoroutineScope.findStackFrame(id: XStackFrameId): FrontendXStackFrame? {
  val storageCache = coroutineContext[FrontendXStackFramesStorage]
  requireNotNull(storageCache) { "StacksStorage not found" }

  return storageCache.findStackFrame(id)
}
