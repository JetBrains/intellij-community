package com.intellij.remoteDev.tests.impl.utils

import com.intellij.diagnostic.ThreadDumper
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.platform.util.coroutines.namedChildScope
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeoutException
import kotlin.time.Duration

@ApiStatus.Internal
val coroutineDumpPrefix = "CoroutineDump:"

@ApiStatus.Internal
suspend fun <T> withTimeoutDumping(title: String,
                                   timeout: Duration,
                                   failMessageProducer: (() -> String)? = null,
                                   action: suspend () -> T): T = coroutineScope {
  val outerScope = namedChildScope(title)

  val deferred = outerScope.async { action() }
  @OptIn(DelicateCoroutinesApi::class)
  withContext(blockingDispatcher) {
    try {
      withTimeout(timeout) { deferred.await() }
    }
    catch (e: TimeoutCancellationException) {
      val message = "$title: Has not finished in $timeout."

      println(buildString {
        appendLine(message)
        // see com.intellij.testFramework.common.TimeoutKt.timeoutRunBlocking
        appendLine(ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos(), false).rawDump)
      })

      val coroutinesDump = dumpCoroutines(outerScope)
      throw TimeoutException(buildString {
        appendLine(message)
        val failMessage = failMessageProducer?.invoke()
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