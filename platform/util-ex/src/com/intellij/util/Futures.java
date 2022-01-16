// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;


@ApiStatus.Experimental
public final class Futures {

  private static final Executor EDT_EXECUTOR = new Executor() {
    @Override
    public void execute(@NotNull Runnable command) {
      final var app = ApplicationManager.getApplication();
      if (app.isDispatchThread()) {
        command.run();
      }
      else {
        app.invokeLater(command);
      }
    }
  };

  private Futures() {
  }

  public static <T> @NotNull CompletableFuture<T> runProgressInBackground(
    @Nullable Project project,
    @NlsContexts.ProgressTitle @NotNull String title,
    boolean canBeCancelled,
    @NotNull Function<ProgressIndicator, T> action,
    @Nullable Runnable onCancel
  ) {
    final var result = new CompletableFuture<T>();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ProgressManager.getInstance().run(new Task.Backgroundable(project, title, canBeCancelled) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            result.complete(action.apply(indicator));
          }
          catch (Throwable t) {
            result.completeExceptionally(t);
          }
        }

        @Override
        public void onCancel() {
          if (onCancel != null) {
            onCancel.run();
          }
        }
      });
    });
    return result;
  }

  public static <T> @NotNull CompletableFuture<T> runAsyncProgressInBackground(
    @Nullable Project project,
    @NlsContexts.ProgressTitle @NotNull String title,
    boolean canBeCancelled,
    @NotNull Function<ProgressIndicator, CompletableFuture<T>> action,
    @Nullable Runnable onCancel
  ) {
    final var result = new CompletableFuture<T>();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ProgressManager.getInstance().run(new Task.Backgroundable(project, title, canBeCancelled) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            final var future = action.apply(indicator);
            future.join();
            result.complete(future.get());
          }
          catch (Throwable t) {
            result.completeExceptionally(t);
          }
        }

        @Override
        public void onCancel() {
          if (onCancel != null) {
            onCancel.run();
          }
        }
      });
    });
    return result;
  }

  public static <T, E extends Throwable> @NotNull CompletableFuture<T> runWriteActionAsync(@NotNull ThrowableComputable<T, E> action) {
    return CompletableFuture.supplyAsync(wrapIntoWriteAction(action), getEdtExecutor());
  }

  public static <E extends Throwable> @NotNull CompletableFuture<Void> runWriteActionAsync(@NotNull ThrowableRunnable<E> action) {
    return CompletableFuture.runAsync(wrapIntoWriteAction(action), getEdtExecutor());
  }

  public static <E extends Throwable> @NotNull CompletionStage<Void> thenRunInWriteAction(
    @NotNull CompletionStage<?> stage,
    @NotNull ThrowableRunnable<E> action
  ) {
    return stage.thenRunAsync(wrapIntoWriteAction(action), getEdtExecutor());
  }

  public static @NotNull Executor getEdtExecutor() {
    return EDT_EXECUTOR;
  }


  private static <T, E extends Throwable> @NotNull Supplier<T> wrapIntoWriteAction(@NotNull ThrowableComputable<T, E> action) {
    return () -> {
      try {
        return WriteAction.compute(action);
      }
      catch (Throwable t) {
        throw new RuntimeException(t);
      }
    };
  }

  private static <E extends Throwable> @NotNull Runnable wrapIntoWriteAction(@NotNull ThrowableRunnable<E> action) {
    return () -> {
      try {
        WriteAction.run(action);
      }
      catch (Throwable t) {
        throw new RuntimeException(t);
      }
    };
  }
}
