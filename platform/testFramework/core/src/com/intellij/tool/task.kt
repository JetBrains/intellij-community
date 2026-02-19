// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tool

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Maps the inputs using [transform] at most [maxConcurrency] at a time until all Jobs are done.
 */
suspend fun <TInput, TOutput> Iterable<TInput>.mapConcurrently(
  maxConcurrency: Int,
  transform: suspend (TInput) -> TOutput,
) = coroutineScope {
  val gate = Semaphore(maxConcurrency)
  this@mapConcurrently.map {
    async {
      gate.withPermit {
        transform(it)
      }
    }
  }.awaitAll()
}