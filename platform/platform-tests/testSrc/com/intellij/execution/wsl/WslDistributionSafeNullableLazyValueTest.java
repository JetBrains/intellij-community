// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import kotlinx.coroutines.Job;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.concurrency.ThreadContext.currentThreadContext;
import static com.intellij.concurrency.ThreadContext.installThreadContext;
import static com.intellij.openapi.progress.ContextKt.prepareThreadContext;
import static java.util.concurrent.TimeUnit.SECONDS;
import static kotlinx.coroutines.JobKt.getJob;

@TestApplication
@Timeout(WslDistributionSafeNullableLazyValueTest.WAIT_TIMEOUT_IN_SECONDS + 10)
public final class WslDistributionSafeNullableLazyValueTest {

  final static long WAIT_TIMEOUT_IN_SECONDS = 30;

  private static <T> void assertEquals(T a, T b) {
    Assertions.assertThat(a).isEqualTo(b);
  }

  @Test
  public void testValueIsComputedAsynchronouslyOnEdt() throws Exception {
    EdtTestUtil.runInEdtAndGet(() -> {
      final var expectedAsyncValue = "Hey!";
      final var lazyValue = WslDistributionSafeNullableLazyValue.create(() -> expectedAsyncValue);

      final var expectedSyncValue = "Not yet!";
      final var syncValue = runWithDummyProgress(() -> lazyValue.getValueOrElse(expectedSyncValue));
      assertEquals(expectedSyncValue, syncValue);

      final var asyncValue = awaitAsyncValue(lazyValue);
      assertEquals(expectedAsyncValue, asyncValue);
      return null;
    });
  }

  @Test
  public void testValueIsComputedAsynchronouslyUnderRA() throws Exception {
    final var expectedAsyncValue = "Hey!";
    final var lazyValue = WslDistributionSafeNullableLazyValue.create(() -> expectedAsyncValue);

    final var expectedSyncValue = "Not yet!";
    final var syncValue = runWithDummyProgress(() -> ReadAction.compute(() -> lazyValue.getValueOrElse(expectedSyncValue)));
    assertEquals(expectedSyncValue, syncValue);

    final var asyncValue = awaitAsyncValue(lazyValue);
    assertEquals(expectedAsyncValue, asyncValue);
  }

  @Test
  public void testValueIsComputedSynchronouslyInPooledThreadNotUnderRA() throws Exception {
    final var expectedValue = "Hey!";
    final var lazyValue = WslDistributionSafeNullableLazyValue.create(() -> expectedValue);

    final var syncValue = runWithDummyProgress(
      () -> {
        var coroutineContext = currentThreadContext();
        return CompletableFuture
          .supplyAsync(
            () -> {
              try (var ignored = installThreadContext(coroutineContext, true)) {
                return lazyValue.getValue();
              }
            },
            AppExecutorUtil.getAppExecutorService())
          .get(WAIT_TIMEOUT_IN_SECONDS, SECONDS);
      });
    assertEquals(expectedValue, syncValue);
  }

  @Test
  public void testValueIsComputedOnlyOnceWhenNoException() throws Exception {
    final var counter = new AtomicInteger(0);
    final var lazyValue = WslDistributionSafeNullableLazyValue.create(() -> {
      TimeoutUtil.sleep(1000);
      return counter.incrementAndGet();
    });

    final int threadCount = getThreadCount();
    launchBackgroundThreadsAndWait(threadCount, () -> runWithDummyProgress(lazyValue::getValue));

    assertEquals(counter.get(), 1);
  }

  @Test
  public void testValueIsReComputedOnException() throws Exception {
    final var counter = new AtomicInteger(0);
    final var lazyValue = WslDistributionSafeNullableLazyValue.create(() -> {
      counter.incrementAndGet();
      throw new RuntimeException("Oh no!");
    });

    final int threadCount = getThreadCount();
    launchBackgroundThreadsAndWait(threadCount, () -> runWithDummyProgress(lazyValue::getValue));

    assertEquals(counter.get(), threadCount);
  }

  @Test
  public void testWarnsAboutNoProgressIndicatorInEdt() {
    EdtTestUtil.runInEdtAndGet(() -> {
      final var lazyValue = WslDistributionSafeNullableLazyValue.create(() -> "nothing");
      var error = LoggedErrorProcessor.executeAndReturnLoggedError(
        () -> safeRethrow(() -> {
          lazyValue.getValue();
          Thread.sleep(2_000); // Sorry, I was too lazy for a proper synchronization.
          return null;
        }));
      Assertions.assertThat(error)
        .hasMessageContaining("not cancellable")
        .hasStackTraceContaining("testWarnsAboutNoProgressIndicatorInEdt");
      return null;
    });
  }

  @Test
  public void testWarnsAboutNoProgressIndicatorInBackground() {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    final var lazyValue = WslDistributionSafeNullableLazyValue.create(() -> "nothing");
    var error = LoggedErrorProcessor.executeAndReturnLoggedError(
      () -> safeRethrow(() -> CompletableFuture.runAsync(() -> safeRethrow(lazyValue::getValue)).get()));
    Assertions.assertThat(error)
      .hasMessageContaining("not cancellable")
      .hasStackTraceContaining("testWarnsAboutNoProgressIndicatorInBackground");
  }

  @SuppressWarnings("BusyWait")
  @Test
  public void testSurvivesProgressCancellationFromEdt() throws Exception {
    EdtTestUtil.runInEdtAndGet(() -> {
      final var counter = new AtomicInteger(0);
      final var finished = new AtomicBoolean(false);
      final int iterations = 500;
      final var lazyValue = WslDistributionSafeNullableLazyValue.create(() -> {
        try {
          while (counter.incrementAndGet() < iterations) {
            ProgressManager.checkCanceled();
            Thread.sleep(10);
          }
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        finally {
          finished.set(true);
        }
        return "finished";
      });

      runWithDummyProgress(() -> {
        Assertions.assertThat(lazyValue.getValueOrElse("not yet")).isEqualTo("not yet");
        Thread.sleep(100);
        getJob(currentThreadContext()).cancel(new CancellationException());
        return null;
      });

      for (int i = 0; i < 100; ++i) {
        if (finished.get()) break;
        Thread.sleep(10);
      }

      Assertions.assertThat(finished.get()).isTrue();
      Assertions.assertThat(counter.get()).isGreaterThan(0).isLessThan(iterations);

      // Checking that the calculation can be restarted, and that there are no unexpected progress cancellations.

      counter.set(iterations - 10);  // 10 iterations in the loop are more than enough for checking the absence of cancellations.
      runWithDummyProgress(() -> {
        Assertions.assertThat(lazyValue.getValueOrElse("not yet")).isEqualTo("not yet");
        return null;
      });

      Assertions.assertThat(awaitAsyncValue(lazyValue)).isEqualTo("finished");
      Assertions.assertThat(counter.get()).isEqualTo(iterations);
      return null;
    });
  }

  @Disabled // TODO
  @Test
  public void testLogsErrorsFromEdt() throws Exception {
    CompletableFuture<Throwable> error = new CompletableFuture<>();

    LoggedErrorProcessor loggedErrorProcessor = new LoggedErrorProcessor() {
      @Override
      public @NotNull Set<Action> processError(
        @NotNull String category,
        @NotNull String message,
        String @NotNull [] details,
        @Nullable Throwable t
      ) {
        return error.complete(t) ? Action.NONE : Action.ALL;
      }
    };
    LoggedErrorProcessor.executeWith(
      loggedErrorProcessor,
      () -> runWithDummyProgress(() -> prepareThreadContext(coroutineContext -> safeRethrow(() -> {
        int sleep = 100;
        final WslDistributionSafeNullableLazyValue<String> lazyValue =
          WslDistributionSafeNullableLazyValue.create(() -> safeRethrow(() -> {
            Thread.sleep(sleep);
            throw new RuntimeException("Oh no!");
          }));

        EdtTestUtil.runInEdtAndGet(() -> {
          try (var ignored = installThreadContext(coroutineContext, false)) {
            final String result = lazyValue.getValueOrElse("not yet");
            Assertions.assertThat(result).isEqualTo("not yet");
            return null;
          }
        });

        error.join();
        return null;
      }))));

    Assertions.assertThat(error.get())
      .describedAs("The exception will never be re-thrown, so it must be logged")
      .hasMessage("Oh no!");
  }

  // Ignored: The test fails because ProgressManager.checkCanceled() doesn't work for an unknown reason.
  @SuppressWarnings("BusyWait")
  @Test
  public void testSurvivesProgressCancellationFromBackground() throws Exception {
    final var counter = new AtomicInteger(0);
    final var finished = new AtomicBoolean(false);
    final int iterations = 500;
    final var lazyValue = WslDistributionSafeNullableLazyValue.create(() -> {
      try {
        while (counter.incrementAndGet() < iterations) {
          ProgressManager.checkCanceled();
          Thread.sleep(10);
        }
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      finally {
        finished.set(true);
      }
      return "finished";
    });

    runWithDummyProgress(() -> {
      return prepareThreadContext(coroutineContext -> safeRethrow(() -> {
        return CompletableFuture
          .allOf(
            CompletableFuture.runAsync(() -> {
              try (var ignored = installThreadContext(coroutineContext, false)) {
                try {
                  lazyValue.getValue();
                  ProgressManager.checkCanceled();
                }
                catch (ProcessCanceledException e) {
                  return;
                }
                Assertions.fail("This line must not execute, because the progress must have been cancelled");
              }
            }),
            CompletableFuture.runAsync(() -> {
              try (var ignored = installThreadContext(coroutineContext, false)) {
                ProgressManager.checkCanceled(); // Should not be canceled by the moment.
                try {
                  Thread.sleep(100);
                }
                catch (InterruptedException e) {
                  // Nothing.
                }
                getJob(currentThreadContext()).cancel(new CancellationException());
              }
            })
          ).get();
      }));
    });

    for (int i = 0; i < 100; ++i) {
      if (finished.get()) break;
      Thread.sleep(10);
    }

    Assertions.assertThat(finished.get()).isTrue();
    Assertions.assertThat(counter.get()).isGreaterThan(0).isLessThan(iterations);

    // Checking that the calculation can be restarted, and that there are no unexpected progress cancellations.

    counter.set(iterations - 10);  // 10 iterations in the loop are more than enough for checking the absence of cancellations.
    runWithDummyProgress(() -> {
      return prepareThreadContext(coroutineContext -> safeRethrow(() -> {
        CompletableFuture
          .runAsync(() -> safeRethrow(() -> {
            try (var ignored = installThreadContext(coroutineContext, false)) {
              Assertions.assertThat(lazyValue.getValue()).isEqualTo("finished");
              ProgressManager.checkCanceled(); // Should not throw.
              return null;
            }
          }))
          .get();
        return null;
      }));
    });

    Assertions.assertThat(counter.get()).isEqualTo(iterations);
  }

  @Test
  public void testAsyncValueComputationSurvivesCancelledJob() {
    var expectedAsyncValue = "Hey!";
    var lazyValue = WslDistributionSafeNullableLazyValue.create(() -> expectedAsyncValue);
    EdtTestUtil.runInEdtAndGet(() -> {
      var expectedSyncValue = "Not yet!";
      var syncValue = runWithDummyProgress(() -> {
        Job job = Objects.requireNonNull(currentThreadContext().get(Job.Key));
        job.cancel(new CancellationException("Not today"));
        return lazyValue.getValueOrElse(expectedSyncValue);
      });
      assertEquals(expectedSyncValue, syncValue);
      return null;
    });

    var syncValue2 = runWithDummyProgress(() -> {
      long start = System.nanoTime();
      do {
        String value = lazyValue.getValue();
        if (value != null) {
          return value;
        }
        Logger.getInstance(WslDistributionSafeNullableLazyValueTest.class).info("Skipped");
        //noinspection BusyWait
        Thread.sleep(10);
      }
      while (System.nanoTime() < start + WAIT_TIMEOUT_IN_SECONDS * 1_000_000_000);
      return null;
    });

    assertEquals(expectedAsyncValue, syncValue2);
  }

  private static int getThreadCount() {
    return Math.max(1, Math.min(10, Runtime.getRuntime().availableProcessors() - 1));
  }

  private static void launchBackgroundThreadsAndWait(final int threadCount, final @NotNull ThrowableComputable<?, Throwable> action)
    throws InterruptedException {
    final var latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; i++) {
      CompletableFuture.runAsync(() -> {
        try {
          safeRethrow(action);
        }
        finally {
          latch.countDown();
        }
      });
    }
    Assertions.assertThat(latch.await(WAIT_TIMEOUT_IN_SECONDS, SECONDS)).describedAs("Wait for threads timed out!").isTrue();
  }

  private static @Nullable String awaitAsyncValue(final @NotNull WslDistributionSafeNullableLazyValue<String> lazyValue) {
    return runWithDummyProgress(() -> {
      long start = System.nanoTime();
      do {
        if (lazyValue.isComputed()) {
          return lazyValue.getValue();
        }
        //noinspection BusyWait
        Thread.sleep(10);
      }
      while (System.nanoTime() < start + WAIT_TIMEOUT_IN_SECONDS * 1_000_000_000);
      return null;
    });
  }

  private static <T> T runWithDummyProgress(@NotNull ThrowableComputable<T, Exception> body) {
    class Holder extends RuntimeException {
      Holder(Throwable t) { super(t); }
    }
    return safeRethrow(() -> {
      try {
        return ProgressManager.getInstance().runProcess(
          () -> prepareThreadContext(coroutineContext -> {
            try (var ignored = installThreadContext(coroutineContext, true)) {
              return body.compute();
            }
            catch (Exception err) {
              throw new Holder(err);
            }
          }),
          new ProgressIndicatorBase());
      }
      catch (Holder e) {
        throw e.getCause();
      }
    });
  }

  private static <T> T safeRethrow(ThrowableComputable<T, ?> body) {
    try {
      return body.compute();
    }
    catch (Throwable e) {
      //noinspection InstanceofCatchParameter
      if (e instanceof ExecutionException) {
        //noinspection AssignmentToCatchBlockParameter
        e = e.getCause();
      }
      ExceptionUtil.rethrowUnchecked(e);
      throw new RuntimeException(e);
    }
  }
}
