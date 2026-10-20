// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqPhaseTimer.Phase
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Keeps the results of the searches of one traversal in [LockReqAnalyzerParallelBFS], so that the traversal runs each search only once.
 *
 * Only one thread runs the search for a key.
 * If other threads need the same key at this time, they wait for the result of this thread.
 * If the search fails or is cancelled, the next request for the key runs the search again.
 * If a cached element becomes invalid, the next request for the key runs the search again.
 *
 * The class is thread-safe.
 */
internal class LockReqSearchCache<K : Any, V : PsiElement> {

  private val entries = ConcurrentHashMap<K, CompletableFuture<List<V>>>()

  /**
   * Gives the cached results for [key], or runs [search] and caches its results.
   * The wait for the search of another thread counts for [Phase.SEARCH_CACHE_WAIT].
   *
   * @param key the key of the search. If the key is `null`, the search runs without the cache.
   * Use `null` when the key does not identify the searched element, for example for a local class.
   * @param onCacheHit is called when the results come from the cache or from the search of another thread
   */
  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun getOrSearch(key: K?, timer: LockReqPhaseTimer, onCacheHit: () -> Unit, search: () -> List<V>): List<V> {
    if (key == null) {
      return search()
    }
    while (true) {
      val newFuture = CompletableFuture<List<V>>()
      val existing = entries.putIfAbsent(key, newFuture)
      if (existing == null) {
        return runSearch(key, newFuture, search)
      }
      val cached = if (existing.isDone) result(existing) else timer.measure(Phase.SEARCH_CACHE_WAIT) { await(existing) }
      if (cached == null) {
        // the search of another thread failed, so this thread tries again
        entries.remove(key, existing)
        continue
      }
      if (cached.all { it.isValid }) {
        onCacheHit()
        return cached
      }
      entries.remove(key, existing)
    }
  }

  private fun runSearch(key: K, future: CompletableFuture<List<V>>, search: () -> List<V>): List<V> {
    try {
      val results = search()
      future.complete(results)
      return results
    }
    catch (e: Throwable) {
      entries.remove(key, future)
      future.completeExceptionally(e)
      throw e
    }
  }

  /**
   * Waits for [future] and keeps the wait cancellable.
   * A pending write action cancels the read action, so the wait does not block the write action.
   *
   * @return the results, or `null` if the search failed
   */
  private fun await(future: CompletableFuture<List<V>>): List<V>? {
    while (true) {
      ProgressManager.checkCanceled()
      try {
        return future.get(WAIT_STEP_MS, TimeUnit.MILLISECONDS)
      }
      catch (_: TimeoutException) {
        // check the cancellation and wait again
      }
      catch (_: ExecutionException) {
        return null
      }
    }
  }

  private fun result(future: CompletableFuture<List<V>>): List<V>? {
    return if (future.isCompletedExceptionally) null else future.getNow(null)
  }

  private companion object {
    const val WAIT_STEP_MS = 10L
  }
}
