package com.intellij.remoteDev.tests.impl.utils

import com.intellij.diagnostic.dumpCoroutines
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
val coroutineDumpPrefix: String = "CoroutineDump:"

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
      //println(buildString {
      //  appendLine(message)
      //  // see com.intellij.testFramework.common.TimeoutKt.timeoutRunBlocking
      //  appendLine(ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos(), false).rawDump)
      //})

      val coroutinesDump = dumpCoroutines(outerScope)
      val failMessage = outerScope.async { failMessageProducer?.invoke() }.await()
      throw TimeoutException(buildString {
        append("$title[timeout=$timeout] failed:")
        appendLine(e.stackTraceToString())
        if (!failMessage.isNullOrBlank()) {
          appendLine(failMessage)
        }
        appendLine("------------")
        appendLine(coroutineDumpPrefix)
        appendLine(coroutinesDump)
        append("------------")
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