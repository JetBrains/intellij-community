// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.openapi.util.IntellijInternalApi
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.BufferedReader
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.time.Duration

/**
 * This should be used instead of [Process.onExit]`().await()`.
 * @return [Process.exitValue]
 */
suspend fun Process.awaitExit(): Int {
  return loopInterruptible { timeout: Duration ->
    if (timeout.isInfinite()) {
      Attempt.success(waitFor())
    }
    else if (waitFor(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)) {
      Attempt.success(exitValue())
    }
    else {
      Attempt.tryAgain()
    }
  }
}

/**
 * Computes and returns result of the [action] which may block for an unforeseeable amount of time.
 *
 * The [action] does not inherit coroutine context from the calling coroutine, use [withContext] to install proper context if needed.
 * The [action] is executed on a special unlimited dispatcher to avoid starving [Dispatchers.IO].
 * The [action] is cancelled if the calling coroutine is cancelled,
 * but this function immediately resumes with CancellationException without waiting for completion of the [action],
 * which means that this function **breaks structured concurrency**.
 *
 * This function is designed to work with native calls which may un-interruptibly hang.
 * Do not run CPU-bound work computations in [action].
 */
@DelicateCoroutinesApi // require explicit opt-in
@IntellijInternalApi
@Internal
suspend fun <T> computeDetached(action: suspend CoroutineScope.() -> T): T {
  val deferred = GlobalScope.async(blockingDispatcher, block = action)
  try {
    return deferred.await()
  }
  catch (ce: CancellationException) {
    deferred.cancel(ce)
    throw ce
  }
}

/**
 * Reads line in suspendable manner.
 * Might be slow, for high performance consider using separate thread and blocking call
 */
@OptIn(DelicateCoroutinesApi::class)
suspend fun BufferedReader.readLineAsync(): String? = computeDetached {
  runInterruptible(blockingDispatcher) {
    readLine()
  }
}

/**
 * Behaves like [InputStream.copyTo], but doesn't block _current_ coroutine context even for a second.
 * Due to unavailability of non-blocking IO for [InputStream], all blocking calls are executed on some daemonic thread, and some I/O
 * operations may outlive current coroutine context.
 *
 * It's safe to set [java.net.Socket.setSoTimeout] if [InputStream] comes from a socket.
 */
@OptIn(DelicateCoroutinesApi::class)
suspend fun InputStream.copyToAsync(outputStream: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE, limit: Long = Long.MAX_VALUE) {
  computeDetached {
    withContext(CoroutineName("copyToAsync: $this => $outputStream")) {
      val buffer = ByteArray(bufferSize)
      var totalRead = 0L
      while (totalRead < limit) {
        yield()
        val read =
          try {
            read(buffer, 0, min(limit - totalRead, buffer.size.toLong()).toInt())
          }
          catch (ignored: SocketTimeoutException) {
            continue
          }
        when {
          read < 0 -> break
          read > 0 -> {
            totalRead += read
            yield()
            // According to Javadoc, Socket.soTimeout doesn't have any influence on SocketOutputStream.
            // Had timeout affected sends, it would have impossible to distinguish if the packets were delivered or not in case of timeout.
            outputStream.write(buffer, 0, read)
          }
          else -> Unit
        }
      }
    }
  }
}