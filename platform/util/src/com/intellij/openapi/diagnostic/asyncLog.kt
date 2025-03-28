// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.openapi.util.coroutines.runSuspend
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

private sealed interface LogQueueItem

internal data class LogEvent(
  val julLogger: java.util.logging.Logger,
  val level: LogLevel,
  val message: String?,
  val throwable: Throwable?,
) : LogQueueItem

private class AwaitQueueEvent(
  val continuation: Continuation<Unit>,
) : LogQueueItem

internal fun LogEvent.log() {
  if (asyncLog != null) {
    asyncLog.log(this)
  }
  else {
    logNow()
  }
}

private fun LogEvent.logNow() {
  val (julLogger, level, message, throwable) = this
  if (throwable != null) {
    julLogger.log(level.level, message, throwable)
  }
  else {
    julLogger.log(level.level, message)
  }
}

private val asyncLog: AsyncLog? = run {
  if (java.lang.Boolean.getBoolean("intellij.platform.log.sync")) {
    null
  }
  else {
    try {
      AsyncLog()
    }
    catch (_: Throwable) {
      // coroutines are not available in JPS
      null
    }
  }
}

@TestOnly
@Internal
fun awaitLogQueueProcessed() {
  asyncLog?.awaitQueueProcessed()
}

internal fun shutdownLogProcessing() {
  asyncLog?.shutdown()
}

private class AsyncLog {

  private val queue: Channel<LogQueueItem> = Channel(capacity = Channel.UNLIMITED)

  private val job: Job = run {
    // separate dispatcher which is outside the 64-thread limit
    @OptIn(ExperimentalCoroutinesApi::class)
    val dispatcher = Dispatchers.IO.limitedParallelism(1)
    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch(dispatcher + CoroutineName("AsyncLog")) {
      for (event in queue) {
        when (event) {
          is LogEvent -> try {
            event.logNow()
          }
          catch (t: Throwable) {
            System.err.println("Logger failure while trying to log $event")
            t.printStackTrace()
          }
          is AwaitQueueEvent -> event.continuation.resume(Unit)
        }
      }
    }
  }

  fun log(event: LogEvent) {
    check(queue.trySend(event).isSuccess)
  }

  fun awaitQueueProcessed() {
    runSuspend {
      suspendCancellableCoroutine {
        check(queue.trySend(AwaitQueueEvent(it)).isSuccess)
      }
    }
  }

  fun shutdown() {
    queue.close()
    runSuspend {
      job.join()
    }
  }
}
