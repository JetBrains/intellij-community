// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.storage

import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.frame.FrontendXExecutionStack
import com.intellij.platform.debugger.impl.rpc.XExecutionStackDto
import com.intellij.platform.debugger.impl.rpc.XExecutionStackId
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Holds one [FrontendXExecutionStack] per [XExecutionStackId] for the lifetime of the owning scope.
 * The backend maps one stack to one id per scope, so the same id always names the same thread.
 */
internal class FrontendXExecutionStacksStorage : AbstractCoroutineContextElement(FrontendXExecutionStacksStorage) {
  companion object Key : CoroutineContext.Key<FrontendXExecutionStacksStorage>

  private val cache = ConcurrentHashMap<XExecutionStackId, FrontendXExecutionStack>()

  /** Returns the stack stored under [id]. Runs [create] at most once per id. */
  fun getOrCreateExecutionStack(id: XExecutionStackId, create: () -> FrontendXExecutionStack): FrontendXExecutionStack {
    return cache.computeIfAbsent(id) { create() }
  }
}

internal fun CoroutineScope.getOrCreateExecutionStack(stackDto: XExecutionStackDto, project: Project): FrontendXExecutionStack {
  val storage = coroutineContext[FrontendXExecutionStacksStorage]
  requireNotNull(storage) { "ExecutionStacksStorage not found" }

  return storage.getOrCreateExecutionStack(stackDto.executionStackId) {
    FrontendXExecutionStack(stackDto, project, this)
  }
}
