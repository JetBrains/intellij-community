// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

/**
 * An {@link ExecutorService} implementation which
 * delegates tasks to the EDT for execution.
 */
public abstract class EdtExecutorService extends AbstractExecutorService {
  public static @NotNull EdtExecutorService getInstance() {
    return EdtExecutorServiceImpl.INSTANCE;
  }

  public static @NotNull ScheduledExecutorService getScheduledExecutorInstance() {
    return EdtScheduledExecutorService.getInstance();
  }

  /**
   * The invocation of this method is equivalent to:
   * <pre>
   * ApplicationManager.getApplication().invokeLater(command, modalityState)
   * </pre>
   *
   * @see com.intellij.openapi.application.Application#invokeLater(Runnable, ModalityState)
   * @deprecated this method is not a part of ExecutorService interface
   */
  @Deprecated(forRemoval = true)
  public abstract void execute(@NotNull Runnable command, @NotNull ModalityState modalityState);

  /**
   * The invocation of this method is equivalent to:
   * <pre>
   * ApplicationManager.getApplication().invokeLater(command, modalityState, expired)
   * </pre>
   *
   * @see com.intellij.openapi.application.Application#invokeLater(Runnable, ModalityState, Condition)
   * @deprecated this method is not a part of ExecutorService interface
   */
  @Deprecated(forRemoval = true)
  public abstract void execute(@NotNull Runnable command, @NotNull ModalityState modalityState, @NotNull Condition<?> expired);

  /**
   * The invocation of this method is equivalent to:
   * <pre>
   * async&lt;Unit&gt;(Dispatchers.EDT + modalityState.asContextElement()) {
   *   blockingContext {
   *     command.run()
   *   }
   * }.asCompletableFuture()
   * </pre>
   *
   * @see com.intellij.openapi.application.CoroutinesKt#getEDT
   * @see com.intellij.openapi.application.CoroutinesKt#asContextElement
   * @see com.intellij.openapi.progress.CoroutinesKt#blockingContext
   * @deprecated this method is not a part of ExecutorService interface
   */
  @Deprecated(forRemoval = true)
  public abstract @NotNull Future<?> submit(@NotNull Runnable command, @NotNull ModalityState modalityState);

  /**
   * The invocation of this method is equivalent to:
   * <pre>
   * async&lt;T&gt;(Dispatchers.EDT + modalityState.asContextElement()) {
   *   blockingContext {
   *     task.call()
   *   }
   * }.asCompletableFuture()
   * </pre>
   *
   * @see com.intellij.openapi.application.CoroutinesKt#getEDT
   * @see com.intellij.openapi.application.CoroutinesKt#asContextElement
   * @see com.intellij.openapi.progress.CoroutinesKt#blockingContext
   * @deprecated this method is not a part of ExecutorService interface
   */
  @Deprecated(forRemoval = true)
  public abstract @NotNull <T> Future<T> submit(@NotNull Callable<T> task, @NotNull ModalityState modalityState);
}
