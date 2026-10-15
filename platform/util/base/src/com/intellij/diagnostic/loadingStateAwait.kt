// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

private val LOADING_STATE_POLL_INTERVAL = 10.milliseconds

/**
 * Suspends until the IDE reaches this loading state.
 *
 * [LoadingState] has no notification of a state change, so the function polls [LoadingState.isOccurred].
 */
@ApiStatus.Internal
suspend fun LoadingState.awaitOccurred() {
  while (!isOccurred) {
    delay(LOADING_STATE_POLL_INTERVAL)
  }
}
