// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
class ProjectInitialActivitiesNotifierImpl(override val project: Project) : ProjectInitialActivitiesNotifier {
  private val _initialVfsRefreshFinished: MutableSharedFlow<Unit> = MutableSharedFlow<Unit>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val initialVfsRefreshFinished: Flow<Unit> get() = _initialVfsRefreshFinished.asSharedFlow().take(1)

  override fun isInitialVfsRefreshFinished(): Boolean = !_initialVfsRefreshFinished.replayCache.isEmpty()

  override suspend fun awaitInitialVfsRefreshFinished() {
    initialVfsRefreshFinished.collect()
  }

  fun notifyInitialVfsRefreshFinished() {
    val notified = _initialVfsRefreshFinished.tryEmit(Unit)
    check(notified)
  }
}