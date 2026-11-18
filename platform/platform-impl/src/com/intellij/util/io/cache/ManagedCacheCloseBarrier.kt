// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.cache

import kotlinx.coroutines.Job
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Holds back the close of a [ManagedPersistentCache] until [job] completes, for at most 10 s.
 * It takes effect in the context of the scope that the cache gets.
 */
class ManagedCacheCloseBarrier(private val job: Job) : AbstractCoroutineContextElement(Key) {

  suspend fun await() {
    job.join()
  }

  companion object Key : CoroutineContext.Key<ManagedCacheCloseBarrier>
}
