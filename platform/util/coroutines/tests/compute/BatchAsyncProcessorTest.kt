// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.coroutines.compute

import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class BatchAsyncProcessorTest {

  @Test
  fun processingTwoBatchesSeparately() {
    timeoutRunBlocking {
      coroutineScope {
        val startSemaphore = Semaphore(1, 1)
        val endCounter = Semaphore(1, 1)
        val batchScope = childScope("batch-scope", Dispatchers.Default)
        val batch = BatchAsyncProcessor<Int, String>(batchScope) { batch ->
          startSemaphore.acquire()
          batch.completeByMapping { it.toString() }
          endCounter.release()
        }

        run {
          val tasks = (1..3).map { i -> batch.submit(i) }
          startSemaphore.release()
          Assertions.assertEquals((1..3).map { it.toString() }, tasks.awaitAll())
          endCounter.acquire()
        }

        run {
          val tasks = (4..6).map { i -> batch.submit(i) }
          startSemaphore.release()
          Assertions.assertEquals((4..6).map { it.toString() }, tasks.awaitAll())
          endCounter.acquire()
        }

        batchScope.coroutineContext.job.cancel()
      }
    }
  }

  @Test
  fun addingTasksDuringProcessing() {
    timeoutRunBlocking {
      coroutineScope {
        val processingSemaphore = Semaphore(3, 3)
        val endCounter = Semaphore(1, 1)
        val batchScope = childScope("batch-scope", Dispatchers.Default)
        val batch = BatchAsyncProcessor<Int, String>(batchScope) { batch ->
          batch.completeByMapping {
            processingSemaphore.acquire()
            it.toString()
          }
          endCounter.release()
        }

        val tasks = buildList {
          add(batch.submit(1))
          add(batch.submit(2))
          processingSemaphore.release()
          add(batch.submit(3))
          processingSemaphore.release()
          processingSemaphore.release()
        }

        Assertions.assertEquals((1..3).map { it.toString() }, tasks.awaitAll())
        endCounter.acquire()

        batchScope.coroutineContext.job.cancel()
      }
    }
  }

  @Test
  fun processingInOwnScope() {
    timeoutRunBlocking {
      coroutineScope {
        val startingSemaphore = Semaphore(3, 3)
        val processingSemaphore = Semaphore(3, 3)
        val endCounter = Semaphore(1, 1)
        val batchScope = childScope("batch-scope", Dispatchers.Default)
        val reallyCancelled = ArrayList<Int>()
        val batch = BatchAsyncProcessor<Int, String>(batchScope) { batch ->
          batch.completeByMapping {
            try {
              startingSemaphore.release()
              processingSemaphore.acquire()
              it.toString()
            } catch (e: CancellationException) {
              reallyCancelled.add(it)
              throw e
            }
          }
          endCounter.release()
        }

        val tasks: List<Deferred<String>> = buildList {
          val scope1 = childScope("scope1", Dispatchers.Default)
          add(batch.submit(scope1.coroutineContext, 1))
          add(batch.submit(2))
          startingSemaphore.acquire()
          scope1.cancel()
          processingSemaphore.release()
          add(batch.submit(3))
          processingSemaphore.release()
          processingSemaphore.release()
        }

        Assertions.assertEquals((2..3).map { it.toString() }, tasks.filter { !it.isCancelled }.awaitAll())
        Assertions.assertEquals(listOf(1), reallyCancelled)
        endCounter.acquire()

        batchScope.coroutineContext.job.cancel()
      }
    }
  }

  @Test
  fun iterateManyTimes() {
    timeoutRunBlocking {
      coroutineScope {
        val firstPhaseIsDone = Semaphore(1, 1)
        val endCounter = Semaphore(1, 1)
        val batchScope = childScope("batch-scope", Dispatchers.Default)
        val batch = BatchAsyncProcessor<Int, String>(batchScope) { batch ->
          val submissions = batch.submissions.toList()
          Assertions.assertTrue(submissions.isNotEmpty())
          batch.completeByMapping { it.toString() }
          firstPhaseIsDone.acquire()
          batch.completeByMapping { it.toString() }
          endCounter.release()
        }

        val tasks = buildList {
          add(batch.submit(1))
          add(batch.submit(2))
          firstPhaseIsDone.release()
          add(batch.submit(3))
        }

        Assertions.assertEquals((1..3).map { it.toString() }, tasks.awaitAll())
        endCounter.acquire()

        batchScope.coroutineContext.job.cancel()
      }
    }
  }

  @Test
  fun iterateOnePerRun() {
    timeoutRunBlocking {
      coroutineScope {
        val endCounter = Semaphore(3, 3)
        val batchScope = childScope("batch-scope", Dispatchers.Default)
        val batch = BatchAsyncProcessor<Int, String>(batchScope) { batch ->
          val submission = batch.submissions.first()
          submission.completeWith(Result.success(submission.input.toString()))
          endCounter.release()
        }

        val tasks = (1..3).map { i -> batch.submit(i) }
        Assertions.assertEquals((1..3).map { it.toString() }, tasks.awaitAll())
        (1..3).forEach { endCounter.acquire() }

        batchScope.coroutineContext.job.cancel()
      }
    }
  }

  @Test
  fun repeatIncomplete() {
    timeoutRunBlocking {
      coroutineScope {
        val firstPhaseIsDone = Semaphore(1, 1)
        val endCounter = Semaphore(1, 1)
        val batchScope = childScope("batch-scope", Dispatchers.Default)
        val batch = BatchAsyncProcessor<Int, String>(batchScope) { batch ->
          waitUntil { batch.submissions.count() >= 2 }
          for (submission in batch.submissions.take(1)) {
            submission.completeWith(Result.success(submission.input.toString()))
          }
          firstPhaseIsDone.acquire()
          batch.completeByMapping { it.toString() }
          endCounter.release()
        }

        val tasks = buildList {
          add(batch.submit(1))
          add(batch.submit(2))
          firstPhaseIsDone.release()
          add(batch.submit(3))
        }

        Assertions.assertEquals((1..3).map { it.toString() }, tasks.awaitAll())
        endCounter.acquire()

        batchScope.coroutineContext.job.cancel()
      }
    }
  }

  @Test
  fun doneInParallel() {
    timeoutRunBlocking {
      coroutineScope {
        val startSemaphore = Semaphore(1, 1)
        val parallelCounter = Channel<Unit>(100)
        val pause = Job()
        val finish = Job()
        val batchScope = childScope("batch-scope", Dispatchers.Default)
        val batch = BatchAsyncProcessor<Int, String>(batchScope) { batch ->
          startSemaphore.acquire()
          batch.completeByMapping(4) { i ->
            parallelCounter.send(Unit)
            pause.join()
            i.toString()
          }
          finish.complete()
        }

        val tasks = (1..3).map { i -> batch.submit(i) }
        startSemaphore.release()
        parallelCounter.consumeAsFlow().take(3).collect()
        pause.complete()

        finish.join()
        Assertions.assertEquals((1..3).map { it.toString() }, tasks.awaitAll())

        batchScope.coroutineContext.job.cancel()
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun allFailed() {
    timeoutRunBlocking {
      coroutineScope {
        val batchScope = childScope("batch-scope", Dispatchers.Default)

        val message = "all failed"
        val batch = BatchAsyncProcessor<Int, String>(batchScope) { batch ->
          throw Exception(message)
        }

        val tasks = (1..3).map { i -> batch.submit(i) }
        Assertions.assertEquals((1..3).map { message }, tasks.map { it.join(); it.getCompletionExceptionOrNull()?.message })
        batchScope.coroutineContext.job.cancel()
      }
    }
  }

}