// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util

import io.opentelemetry.context.Context
import java.util.concurrent.*
import java.util.function.Function

object GradleExecutorServiceUtil {

  @JvmStatic
  fun <T> withSingleThreadExecutor(name: String, action: Function<ExecutorService, T>): T {
    val executorService = Executors.newSingleThreadExecutor {
      Thread(it, name)
    }
    try {
      return action.apply(executorService)
    }
    finally {
      executorService.shutdown()
      try {
        executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)
      }
      catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
      }
    }
  }

  @JvmStatic
  fun <T> submitTask(
    executor: ExecutorService,
    queue: BlockingQueue<Future<T>>,
    task: Callable<T>,
  ) {
    val taskResult = Context.current()
      .wrap(executor)
      .submit(task)
    queue.add(taskResult)
  }

  @JvmStatic
  fun <T> pollAllPendingResults(queue: BlockingQueue<Future<T>>): MutableList<T> {
    val results = ArrayList<T>()
    var result = poolPendingResult(queue)
    while (result != null) {
      results.add(result)
      result = poolPendingResult(queue)
    }
    return results
  }

  @JvmStatic
  fun <T> poolPendingResult(queue: BlockingQueue<Future<T>>): T? {
    try {
      val future = queue.poll()
      if (future == null) {
        return null
      }
      return future.get()
    }
    catch (ignored: InterruptedException) {
      return null
    }
    catch (ignored: ExecutionException) {
      return null
    }
  }
}
