/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;

/**
 * Executes tasks on pooled threads. At any point, at most {@code maxThreads} threads will be active processing tasks.
 * If additional tasks are submitted when all threads are active, they will wait in the queue until a thread is available.
 *
 * Difference to {@link java.util.concurrent.Executors#newFixedThreadPool(int)} is that this utility class
 * allows to reuse shared thread pool and thus getting rid of extra thread creation and thread pool management.
 *
 * @param <T> the type of elements
 */
public class MergingBackgroundExecutor<T> {
  private final Consumer<T> myConsumer;
  private final ExecutorService myExecutorService;

  public MergingBackgroundExecutor(int maxThreads, @NotNull Consumer<T> consumer) {
    myConsumer = consumer;
    myExecutorService = AppExecutorUtil.createBoundedApplicationPoolExecutor(maxThreads);
  }


  public void queue(@NotNull final T t) {
    myExecutorService.execute(() -> myConsumer.consume(t));
  }

  @NotNull
  public static MergingBackgroundExecutor<Runnable> newRunnableExecutor(int maxThreads) {
    return new MergingBackgroundExecutor<>(maxThreads, runnable -> runnable.run());
  }
}
