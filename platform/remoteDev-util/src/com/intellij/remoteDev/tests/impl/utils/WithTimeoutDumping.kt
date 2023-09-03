package com.intellij.remoteDev.tests.impl

import com.intellij.diagnostic.dumpCoroutines
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeoutException
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration

@ApiStatus.Internal
suspend fun <T> withTimeoutDumping(title: String,
                                   timeout: Duration,
                                   failMessageProducer: (() -> String)? = null,
                                   action: suspend () -> T): T {
  val outerScope = CoroutineScope(coroutineContext + CoroutineName(title))

  val deferred = outerScope.async { action() }
  coroutineScope {
    launch {
      try {
        withTimeout(timeout) { deferred.await() }
      }
      catch (e: TimeoutCancellationException) {
        val coroutinesDump = dumpCoroutines(outerScope)
        deferred.cancel(e)
        throw TimeoutException("$title: Has not finished in $timeout.\n" +
                               "------------\n" +
                               "CoroutineDump:\n" +
                               coroutinesDump + "\n" +
                               "------------" +
                               failMessageProducer?.invoke()?.let { "\n$it" }.orEmpty())
      }
    }
  }
  return deferred.await()
}