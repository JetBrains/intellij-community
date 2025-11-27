// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util

import com.intellij.util.ExceptionUtilRt
import io.opentelemetry.context.Context
import org.jetbrains.plugins.gradle.tooling.Exceptions
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
      catch (_: InterruptedException) {
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
    var next = drainEntry(queue)
    while (next != null) {
      next.onSuccess {
        results.add(it)
      }
      next = drainEntry(queue)
    }
    return results
  }

  @JvmStatic
  fun <T> firstOrNull(queue: BlockingQueue<Future<T>>): T? = drainEntry(queue)?.getOrNull()

  private fun <T> drainEntry(queue: BlockingQueue<Future<T>>): Result<T>? {
    return runCatching {
      val future = queue.poll()
      if (future == null) {
        return null
      }
      return@runCatching future.get()
    }.onFailure {
      if (it is ExecutionException) {
        val exception = Exceptions.unwrap(it)
        System.err.println(ExceptionUtilRt.getThrowableText(exception, "org.jetbrains."))
      }
    }
  }
}
