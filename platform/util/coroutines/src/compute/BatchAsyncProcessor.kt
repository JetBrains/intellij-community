// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.coroutines.compute

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@ApiStatus.Internal
class BatchAsyncProcessor<T, R>(coroutineScope: CoroutineScope, private val processor: suspend (Batch<T, R>) -> Unit) {

  private val queue: Channel<Submission<T, R>> = Channel(Channel.UNLIMITED)

  init {
    coroutineScope.launch {
      val pendingSubmissions = ArrayList<Submission<T, R>>()

      val batch = object : Batch<T, R> {
        override val submissions: Sequence<Submission<T, R>>
          get() = sequence {
            yieldAll(pendingSubmissions.asSequence().filter { !it.isDone })
            while (isActive) {
              val receiveResult = queue.tryReceive()
              if (receiveResult.isFailure) break
              val task = receiveResult.getOrThrow()
              if (!task.isDone) {
                pendingSubmissions.add(task)
                yield(task)
              }
            }
          }
      }

      while (isActive) {
        while (isActive) {
          val first = queue.receive()
          if (!first.isDone) {
            pendingSubmissions.add(first)
            break
          }
        }
        try {
          processor.invoke(batch)
          val incompleteSubmissions = pendingSubmissions.filter { !it.isDone }
          if (incompleteSubmissions.isNotEmpty()) {
            val failure = Result.failure<R>(CancellationException("was ignored by batch processor"))
            incompleteSubmissions.forEach { it.completeWith(failure) }
          }
          pendingSubmissions.clear()
        }
        catch (e: Throwable) {
          val failure = Result.failure<R>(e)
          pendingSubmissions.forEach { it.completeWith(failure) }
          pendingSubmissions.clear()
          coroutineContext.ensureActive()
        }
      }

    }.invokeOnCompletion { queue.close(it) }
  }

  fun submit(element: T): Deferred<R> = submit(EmptyCoroutineContext, element)

  fun submit(context: CoroutineContext, element: T): Deferred<R> {
    val output = CompletableDeferred<R>(context[Job])
    val sendResult = queue.trySend(Submission(element, context, output))
    if (sendResult.isFailure) {
      output.completeExceptionally(sendResult.exceptionOrNull() ?: CancellationException("can't put to queue"))
    }
    return output
  }

  interface Batch<T, R> {

    val submissions: Sequence<Submission<T, R>>

  }

  class Submission<T, R> internal constructor(val input: T, private val context: CoroutineContext, private val output: CompletableDeferred<R>) {

    val isDone: Boolean get() = output.isCompleted

    fun completeWith(result: Result<R>) {
      output.completeWith(result)
    }

    suspend fun completeBy(f: suspend (T) -> R) {
      output.completeWith(runCatching {
        withContext(context) {
          f(input)
        }
      })
    }

  }
}

@ApiStatus.Internal
suspend fun <T, R> BatchAsyncProcessor.Batch<T, R>.completeByMapping(parallelism: Int, f: suspend (T) -> R) {
  check(parallelism >= 1) { "Wrong parallelism: $parallelism" }
  val iterator = this.submissions

  coroutineScope {
    val semaphore = Semaphore(parallelism)
    iterator.forEach { submission ->
      semaphore.acquire()
      launch {
        submission.completeBy(f)
      }.invokeOnCompletion {
        semaphore.release()
      }
    }
  }
}

@ApiStatus.Internal
suspend fun <T, R> BatchAsyncProcessor.Batch<T, R>.completeByMapping(f: suspend (T) -> R) {
  for (submission in submissions) {
    submission.completeBy(f)
  }
}

@ApiStatus.Internal
fun <T, R> BatchAsyncProcessor.Batch<T, R>.completeByMappingBlocking(f: (T) -> R) {
  for (submission in submissions) {
    submission.completeWith(runCatching { f(submission.input) })
  }
}