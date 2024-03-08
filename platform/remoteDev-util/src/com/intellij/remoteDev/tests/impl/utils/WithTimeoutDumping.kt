package com.intellij.remoteDev.tests.impl

import com.intellij.diagnostic.ThreadDumper
import com.intellij.diagnostic.dumpCoroutines
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeoutException
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration

@ApiStatus.Internal
val coroutineDumpPrefix = "CoroutineDump:"

@ApiStatus.Internal
val threadsDumpPrefix = "ThreadsDump:"

@ApiStatus.Internal
suspend fun <T> withTimeoutDumping(title: String,
                                   timeout: Duration,
                                   failMessageProducer: (() -> String)? = null,
                                   action: suspend () -> T): T {
  val outerScope = CoroutineScope(coroutineContext + CoroutineName(title))

  val deferred = outerScope.async { action() }
  coroutineScope {
    launch(Dispatchers.IO) {
      try {
        withTimeout(timeout) { deferred.await() }
      }
      catch (e: TimeoutCancellationException) {
        val coroutinesDump = dumpCoroutines(outerScope)
        val threadsDump = ThreadDumper.dumpThreadsToString()
        deferred.cancel(e)
        throw TimeoutException(buildString {
          append("$title: Has not finished in $timeout.\n")
          val failMessage = failMessageProducer?.invoke()
          if (!failMessage.isNullOrBlank()) {
            append("$failMessage\n")
          }
          append("------------\n")
          append("$coroutineDumpPrefix\n")
          append("$coroutinesDump\n")
          append("$threadsDumpPrefix\n")
          append("$threadsDump\n")
          append("------------")
        })
      }
    }
  }
  return deferred.await()
}