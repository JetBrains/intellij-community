// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.warmup.util

import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import java.io.File
import java.time.Duration
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlin.math.max

class TimeCookie {
  private val now = System.currentTimeMillis()
  fun formatDuration(): String {
    val duration = max(0L, TimeCookie().now - this.now)
    return StringUtil.formatDuration(duration)
  }
}

private object TaskAndLogTimeKey : CoroutineContext.Key<TaskAndLogTimeElement>
private class TaskAndLogTimeElement : CoroutineContext.Element {
  private val taskStack = ArrayDeque<String>()
  override val key: CoroutineContext.Key<*>
    get() = TaskAndLogTimeKey

  fun push(progress: String) {
    synchronized(taskStack) {
      taskStack.push(progress)
    }
  }

  fun logStack(currentProgress: String) = synchronized(taskStack) {
    if (taskStack.peek() == currentProgress) {
      taskStack.reversed().joinToString(" / ")
    }
    else null
  }

  fun pop(progress: String) {
    synchronized(taskStack) {
      taskStack.remove(progress)
    }
  }
}

suspend fun <Y> runTaskAndLogTime(
  progressName: String,
  action: suspend CoroutineScope.(TimeCookie) -> Y
): Y = coroutineScope {
  val cookie = TimeCookie()
  ConsoleLog.info("Waiting for $progressName...")

  val stackElement = coroutineContext[TaskAndLogTimeKey] ?: TaskAndLogTimeElement()
  stackElement.push(progressName)

  @Suppress("EXPERIMENTAL_API_USAGE")
  val loggerJob = GlobalScope.launch(Dispatchers.IO) {
    launch {
      while (true) {
        delay(Duration.ofSeconds(5))
        stackElement.logStack(progressName)?.let { message ->
          ConsoleLog.info("... keep running ${message}.... so far ${cookie.formatDuration()}")
        }
      }
    }

    launch {
      while (true) {
        delay(Duration.ofMinutes(5))
        val message = stackElement.logStack(progressName) ?: continue
        val prefix = "keep running ${message}... so far ${cookie.formatDuration()}"
        val threadDump = ThreadDumper.dumpThreadsToString()
        ConsoleLog.info("Printing a threadDump for diagnostic in case of process' hanging")
        ConsoleLog.info("... $prefix\n$threadDump")

        try {
          val logFile = File(PathManager.getLogPath(), "too-long-wait-thread-dump-${System.currentTimeMillis()}.txt")
          logFile.parentFile?.mkdirs()
          logFile.writeText(prefix + "\n\n" + threadDump)
        }
        catch (t: Throwable) {
          //NOP
        }
      }
    }
  }

  try {
    withContext(stackElement + CoroutineName(progressName)) {
      action(cookie)
    }
  }
  finally {
    loggerJob.cancelAndJoin()
    stackElement.pop(progressName)
    ConsoleLog.info("Completed waiting for $progressName in ${cookie.formatDuration()}")
  }
}
