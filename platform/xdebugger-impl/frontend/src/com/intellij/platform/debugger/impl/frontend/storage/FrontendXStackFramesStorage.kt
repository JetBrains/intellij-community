// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.storage

import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.frame.FrontendXStackFrame
import com.intellij.platform.debugger.impl.rpc.XStackFrameDto
import com.intellij.platform.debugger.impl.rpc.XStackFramePresentation
import com.intellij.xdebugger.impl.rpc.XStackFrameId
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class FrontendXStackFramesStorage : AbstractCoroutineContextElement(FrontendXStackFramesStorage) {
  companion object Key : CoroutineContext.Key<FrontendXStackFramesStorage>

  private val cache = ConcurrentHashMap<XStackFrameId, ImmutableFrameWithMutablePresentation>()

  fun getOrCreateStackFrame(project: Project, scope: CoroutineScope, frameDto: XStackFrameDto): FrontendXStackFrame {
    val (id, sourcePosition, equalityObject, evaluator, initialPresentation, captionInfo, customBackgroundInfo, canDrop) = frameDto
    val frameWithMutablePresentation = cache.computeIfAbsent(id) {
      val frame = FrontendXStackFrame(
        id,
        project,
        scope,
        sourcePosition,
        customBackgroundInfo,
        equalityObject,
        evaluator,
        captionInfo,
        canDrop,
      )
      ImmutableFrameWithMutablePresentation(frame, initialPresentation)
    }
    frameWithMutablePresentation.presentation = frameDto.initialPresentation
    return frameWithMutablePresentation.frame
  }

  fun currentPresentation(id: XStackFrameId): XStackFramePresentation? = cache[id]?.presentation

  private class ImmutableFrameWithMutablePresentation(val frame: FrontendXStackFrame, var presentation: XStackFramePresentation)
}

internal fun CoroutineScope.getOrCreateStackFrame(frameDto: XStackFrameDto, project: Project): FrontendXStackFrame {
  val storageCache = coroutineContext[FrontendXStackFramesStorage]
  requireNotNull(storageCache) { "StacksStorage not found" }

  return storageCache.getOrCreateStackFrame(project, this, frameDto)
}

internal fun CoroutineScope.currentPresentation(id: XStackFrameId): XStackFramePresentation? {
  val storageCache = coroutineContext[FrontendXStackFramesStorage]
  requireNotNull(storageCache) { "StacksStorage not found" }

  return storageCache.currentPresentation(id)
}
