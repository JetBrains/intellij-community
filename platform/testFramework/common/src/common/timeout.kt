// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.diagnostic.ThreadDumper
import com.intellij.util.DebugAttachDetectorArgs
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.TestOnly
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@TestOnly
val DEFAULT_TEST_TIMEOUT: Duration = 10.seconds

@TestOnly
@JvmField
val DEFAULT_TEST_TIMEOUT_MS: Long = DEFAULT_TEST_TIMEOUT.inWholeMilliseconds

/**
 * Runs a suspending block of code with a specified timeout within the given coroutine context.
 * During debug session, the timeout is ignored.
 */
@TestOnly
fun <T> timeoutRunBlocking(
  timeout: Duration = DEFAULT_TEST_TIMEOUT,
  coroutineName: String? = null,
  context: CoroutineContext = EmptyCoroutineContext,
  action: suspend CoroutineScope.() -> T,
): T {
  val nameContext = coroutineName?.let(::CoroutineName) ?: EmptyCoroutineContext
  @Suppress("RAW_RUN_BLOCKING")
  return runBlocking(context = context + nameContext) {
    val job = async(block = action)
    @OptIn(DelicateCoroutinesApi::class)
    withContext(blockingDispatcher) {
      try {
        val value = when {
          DebugAttachDetectorArgs.isAttached() -> job.await()
          else -> withTimeout(timeout) {
            job.await()
          }
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
