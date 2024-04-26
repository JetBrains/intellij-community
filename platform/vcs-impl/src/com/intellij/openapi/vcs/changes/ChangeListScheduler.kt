// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class ChangeListScheduler(private val coroutineScope: CoroutineScope) {
  // @TestOnly
  private val isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode
  private val jobs = ArrayDeque<Job>()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val limitedDispatcher = Dispatchers.IO.limitedParallelism(1)

  fun schedule(command: Runnable, delay: Long, unit: TimeUnit) {
    val future = coroutineScope.launch(limitedDispatcher) {
      delay(unit.toMillis(delay))
      blockingContext {
        command.run()
      }
    }
    addFuture(future)
  }

  fun submit(command: Runnable) {
    val future = coroutineScope.launch(limitedDispatcher) {
      blockingContext {
        command.run()
      }
    }
    addFuture(future)
  }

  private fun addFuture(future: Job) {
    if (isUnitTestMode) {
      synchronized(jobs) { jobs.add(future) }
    }
  }

  @TestOnly
  fun cancelAll() {
    synchronized(jobs) {
      for (future in jobs) {
        future.cancel()
      }
      jobs.clear()
    }
  }

  @TestOnly
  fun awaitAllAndStop() {
    awaitAll()
    synchronized(jobs) {
      cancelAll()
    }
  }

  @TestOnly
  fun awaitAll() {
    val throwables = ArrayList<Throwable>()
    val start = System.currentTimeMillis()
    while (true) {
      if (System.currentTimeMillis() - start > TimeUnit.MINUTES.toMillis(10)) {
        cancelAll()
        throwables.add(IllegalStateException("Too long waiting for VCS update"))
        break
      }
      var future: Job?
      synchronized(jobs) { future = jobs.peek() }
      if (future == null) break
      if (ApplicationManager.getApplication().isDispatchThread) {
        EDT.dispatchAllInvocationEvents()
      }
      try {
        future!!.asCompletableFuture().get(10, TimeUnit.MILLISECONDS)
      }
      catch (ignore: TimeoutException) {
        continue
      }
      catch (ignored: CancellationException) {
      }
      catch (e: InterruptedException) {
        throwables.add(e)
      }
      catch (e: ExecutionException) {
        throwables.add(e)
      }
      synchronized(jobs) { jobs.remove(future) }
    }
    CompoundRuntimeException.throwIfNotEmpty(throwables)
  }
}