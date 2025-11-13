// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

public final class GradleExecutorServiceUtil {

  public static <T> T withSingleThreadExecutor(@NotNull String name, @NotNull Function<ExecutorService, T> action) {
    ExecutorService executorService = Executors.newSingleThreadExecutor(new SimpleThreadFactory(name));
    try {
      return action.apply(executorService);
    }
    finally {
      executorService.shutdown();
      try {
        executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public static <T> void submitTask(
    @NotNull ExecutorService executor,
    @NotNull BlockingQueue<Future<T>> queue,
    @NotNull Callable<T> task
  ) {
    Future<T> taskResult = Context.current()
      .wrap(executor)
      .submit(task);
    queue.add(taskResult);
  }

  public static <T> @NotNull List<T> pollAllPendingResults(@NotNull BlockingQueue<Future<T>> queue) {
    List<T> results = new ArrayList<>();
    T result = poolPendingResult(queue);
    while (result != null) {
      results.add(result);
      result = poolPendingResult(queue);
    }
    return results;
  }

  public static <T> @Nullable T poolPendingResult(@NotNull BlockingQueue<Future<T>> queue) {
    try {
      Future<T> future = queue.poll();
      if (future == null) {
        return null;
      }
      return future.get();
    }
    catch (InterruptedException | ExecutionException ignored) {
      return null;
    }
  }

  // Use this static class as a simple ThreadFactory to prevent a memory leak when passing an anonymous ThreadFactory object to
  // Executors.newSingleThreadExecutor. Memory leak will occur on the Gradle Daemon otherwise.
  private static final class SimpleThreadFactory implements ThreadFactory {

    private final String myName;

    private SimpleThreadFactory(@NotNull String name) {
      myName = name;
    }

    @Override
    public Thread newThread(@NotNull Runnable runnable) {
      return new Thread(runnable, myName);
    }
  }
}
