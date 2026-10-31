// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class HelpAvailability(
  private val probe: suspend (String) -> Boolean = { probeUrl(it) },
  private val nanoTime: () -> Long = System::nanoTime,
) {
  private data class Result(val available: Boolean, val expiresAt: Long)
  private val results = LinkedHashMap<String, Result>()
  private val mutex = Mutex()

  suspend fun isAvailable(url: String): Boolean = mutex.withLock {
    val previous = results[url]
    if (previous != null && nanoTime() - previous.expiresAt < 0) return@withLock previous.available
    val available = probe(url)
    if (results.size >= 64) results.remove(results.keys.first())
    results[url] = Result(available, nanoTime() + TimeUnit.SECONDS.toNanos(30))
    available
  }

  companion object {
    private suspend fun probeUrl(url: String): Boolean = runInterruptible(Dispatchers.IO) {
      try {
        HttpRequests.request(url).connectTimeout(1500).readTimeout(1500).redirectLimit(3).tryConnect() in 200..299
      }
      catch (_: IOException) {
        false
      }
    }
  }
}
