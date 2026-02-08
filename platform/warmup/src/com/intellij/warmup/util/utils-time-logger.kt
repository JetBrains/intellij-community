// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.text.Formats
import com.intellij.util.application
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlin.math.max

internal class TimeCookie {
  private val now = System.currentTimeMillis()
  fun formatDuration(): String {
    val duration = max(0L, TimeCookie().now - this.now)
    return Formats.formatDuration(duration)
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

@OptIn(DelicateCoroutinesApi::class)
suspend fun <Y> runTaskAndLogTime(
  progressName: String,
  action: suspend CoroutineScope.() -> Y
): Y = coroutineScope {
  val cookie = TimeCookie()
  WarmupLogger.logInfo("Started waiting for '$progressName'...")

  val stackElement = coroutineContext[TaskAndLogTimeKey] ?: TaskAndLogTimeElement()
  stackElement.push(progressName)

  val loggerJob = application.service<WarmupScopeService>().launch(Dispatchers.IO) {
    launch {
      while (true) {
        delay(Duration.ofSeconds(5))
        stackElement.logStack(progressName)?.let { message ->
          // don't print it to logs, too noisy
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
      action()
    }
  }
  finally {
    loggerJob.cancelAndJoin()
    stackElement.pop(progressName)
    WarmupLogger.logInfo("Completed waiting for '$progressName' in ${cookie.formatDuration()}")
  }
}

@Service
private class WarmupScopeService(coroutineScope: CoroutineScope) : CoroutineScope by coroutineScope
