package com.intellij.remoteDev.tests.impl.utils

import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.TimeoutException
import kotlin.io.path.appendText
import kotlin.io.path.createFile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
val threadDumpFileNameSubstring: String = "thread-dump"

@ApiStatus.Internal
suspend fun <T> withTimeoutDumping(
  title: String,
  timeout: Duration,
  failMessageProducer: (() -> String)? = null,
  action: suspend () -> T,
): T = coroutineScope {
  val outerScope = childScope(title)

  val deferred = outerScope.async(CoroutineName("withTimeoutDumping#$title")) { action() }
  @OptIn(DelicateCoroutinesApi::class)
  withContext(blockingDispatcher) {
    try {
      withTimeout(timeout) { deferred.await() }
    }
    catch (e: TimeoutCancellationException) {
      val threadDump = ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos(), true).rawDump
      val insideIde = ApplicationManager.getApplication() != null
      val fileWithThreadDump = if (insideIde) {
        val logDir = PathManager.getLogDir()
        logDir.resolve(getArtifactsFileName(title, threadDumpFileNameSubstring, "log"))
      } else null

      if (fileWithThreadDump != null) {
        fileWithThreadDump.createFile()
        fileWithThreadDump.appendText(threadDump)
      }
      val failMessage = outerScope.async { failMessageProducer?.invoke() }.await()
      throw TimeoutException(buildString {
        append("$title[timeout=$timeout] failed:")
        appendLine(e.stackTraceToString())
        if (!failMessage.isNullOrBlank()) {
          appendLine(failMessage)
        }
        if (fileWithThreadDump != null) {
          appendLine("Thread dump can be found at file:///${fileWithThreadDump}")
        }
        else {
          appendLine("------------")
          appendLine(threadDump)
          append("------------")
        }
      })
    }
  }.also {
    outerScope.cancel()
  }
}

@TestOnly
@ApiStatus.Internal
internal suspend fun waitSuspending(
  subjectOfWaiting: String,
  timeout: Duration,
  delay: Duration = 500.milliseconds,
  onFailure: (() -> Unit),
  checker: suspend () -> Boolean,
): Boolean {
  return runCatching {
    runLogged("$subjectOfWaiting with $timeout timeout") {
      withTimeoutDumping(
        title = subjectOfWaiting,
        timeout = timeout,
        action = {
          while (!checker()) {
            delay(delay)
          }
        },
      )
    }
  }
    .onFailure { onFailure.invoke() }
    .isSuccess
}