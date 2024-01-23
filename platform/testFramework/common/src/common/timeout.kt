// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.diagnostic.ThreadDumper
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@TestOnly
val DEFAULT_TEST_TIMEOUT: Duration = 10.seconds

@TestOnly
fun <T> timeoutRunBlocking(
  timeout: Duration = DEFAULT_TEST_TIMEOUT,
  coroutineName: String? = null,
  action: suspend CoroutineScope.() -> T,
): T {
  @Suppress("RAW_RUN_BLOCKING")
  return runBlocking(context = coroutineName?.let(::CoroutineName) ?: EmptyCoroutineContext) {
    val job = async(block = action)
    @OptIn(DelicateCoroutinesApi::class)
    withContext(blockingDispatcher) {
      try {
        val value = withTimeout(timeout) {
          job.await()
        }
        Result.success(value)
      }
      catch (e: TimeoutCancellationException) {
        println(ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos(), false).rawDump)
        job.cancel(e)
        Result.failure(AssertionError(e))
      }
    }
  }.getOrThrow()
}
