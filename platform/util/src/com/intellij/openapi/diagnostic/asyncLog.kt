// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.platform.util.coroutines.internal.runSuspend
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

internal data class LogEvent(
  val julLogger: java.util.logging.Logger,
  val level: LogLevel,
  val message: String?,
  val throwable: Throwable?,
)

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
    julLogger.log(level.level, message, throwable);
  }
  else {
    julLogger.log(level.level, message);
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

internal fun shutdownLogProcessing() {
  asyncLog?.shutdown()
}

private class AsyncLog {

  private val queue: Channel<LogEvent> = Channel(capacity = Channel.UNLIMITED)

  private val job: Job = run {
    // separate dispatcher which is outside the 64-thread limit
    @OptIn(ExperimentalCoroutinesApi::class)
    val dispatcher = Dispatchers.IO.limitedParallelism(1)
    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch(dispatcher + CoroutineName("AsyncLog")) {
      for (event in queue) {
        event.logNow()
      }
    }
  }

  fun log(event: LogEvent) {
    check(queue.trySend(event).isSuccess)
  }

  fun shutdown() {
    queue.close()
    runSuspend {
      job.join()
    }
  }
}
