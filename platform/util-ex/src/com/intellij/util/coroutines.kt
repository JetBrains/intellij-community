// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.util.IntellijInternalApi
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Awaits cancellation of [this] scope, and executes [action] in the context dispatcher of the scope after the scope is cancelled.
 * NB: this function prevents normal completion of the scope, so it should be used for scopes which don't complete normally anyway.
 *
 * Standard [Job.invokeOnCompletion] does not provide any threading guarantees,
 * but there are cases when the cleanup action is expected to be invoked on a certain thread (e.g. EDT).
 * See https://github.com/Kotlin/kotlinx.coroutines/issues/3505
 *
 * TODO consider suspend [action] when a use case arrives
 */
@IntellijInternalApi
@Internal
@Experimental
fun CoroutineScope.awaitCancellation(action: () -> Unit) {
  // UNDISPATCHED guarantees that the coroutine will execute until the first suspension point (awaitCancellation)
  launch(start = CoroutineStart.UNDISPATCHED) {
    try {
      awaitCancellation()
    }
    finally {
      withContext(NonCancellable) {
        // yield forces re-dispatch guaranteeing that the action won't be executed right away
        // in case the current scope was cancelled concurrently
        yield()
        action()
      }
    }
  }
}
