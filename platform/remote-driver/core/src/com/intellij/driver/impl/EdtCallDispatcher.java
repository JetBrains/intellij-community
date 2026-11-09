package com.intellij.driver.impl;

import com.intellij.openapi.application.ModalityState;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Runs a Driver call on the EDT in the modality that is current when the call can run.
 * <p>
 * The current modality can only be read on the EDT, so it is read in one EDT event and the call is queued with it afterwards.
 * A modal dialog that opens before the queued call runs, for example from an action the test has just invoked, holds the call
 * until the dialog closes, and a test that waits for the call cannot close the dialog. So while the call has not started, it is
 * queued again in the current modality whenever that modality would hold the copy queued before. The first copy that runs
 * performs the call, and the others do nothing.
 */
final class EdtCallDispatcher {
  private static final long MODALITY_CHECK_INTERVAL_MS = 50;

  private EdtCallDispatcher() {
  }

  /**
   * @param call            the call, performed once on the EDT
   * @param currentModality reads the current modality on the EDT
   * @param dispatch        queues a runnable on the EDT in a modality and waits for it, as {@code Application.invokeAndWait} does
   * @param executor        runs each dispatch, so that a copy held by a modal dialog does not keep the caller waiting
   */
  static void invokeAndWait(@NotNull Runnable call,
                            @NotNull Supplier<? extends ModalityState> currentModality,
                            @NotNull BiConsumer<? super Runnable, ? super ModalityState> dispatch,
                            @NotNull Executor executor) {
    AtomicBoolean claimed = new AtomicBoolean();
    CompletableFuture<Void> done = new CompletableFuture<>();
    ModalityState queued = currentModality.get();
    queue(call, queued, claimed, done, dispatch, executor);
    while (true) {
      try {
        done.get(MODALITY_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        return;
      }
      catch (TimeoutException e) {
        if (!claimed.get()) {
          ModalityState current = currentModality.get();
          if (!current.accepts(queued)) {
            queued = current;
            queue(call, queued, claimed, done, dispatch, executor);
          }
        }
      }
      catch (ExecutionException e) {
        ExceptionUtil.rethrowUnchecked(e.getCause());
        throw new RuntimeException(e.getCause());
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
  }

  private static void queue(@NotNull Runnable call,
                            @NotNull ModalityState modality,
                            @NotNull AtomicBoolean claimed,
                            @NotNull CompletableFuture<Void> done,
                            @NotNull BiConsumer<? super Runnable, ? super ModalityState> dispatch,
                            @NotNull Executor executor) {
    executor.execute(() -> {
      AtomicBoolean ran = new AtomicBoolean();
      try {
        dispatch.accept((Runnable)() -> {
          if (claimed.compareAndSet(false, true)) {
            ran.set(true);
            call.run();
          }
        }, modality);
        if (ran.get()) {
          done.complete(null);
        }
      }
      catch (Throwable e) {
        // a dispatch that fails before any copy has run fails the call, as a failed invokeAndWait did
        if (ran.get() || claimed.compareAndSet(false, true)) {
          done.completeExceptionally(e);
        }
      }
    });
  }
}
