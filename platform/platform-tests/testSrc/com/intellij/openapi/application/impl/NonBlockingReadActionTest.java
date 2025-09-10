// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.ide.startup.ServiceNotReadyException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.*;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.ui.UIUtil;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promise;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.intellij.testFramework.PlatformTestUtil.waitForPromise;

public class NonBlockingReadActionTest extends LightPlatformTestCase {

  public void testCoalesceEqual() {
    Object same = new Object();
    CancellablePromise<String> promise = WriteAction.compute(() -> {
      CancellablePromise<String> promise1 =
        ReadAction.nonBlocking(() -> "y").coalesceBy(same).submit(AppExecutorUtil.getAppExecutorService());
      assertFalse(promise1.isCancelled());

      CancellablePromise<String> promise2 =
        ReadAction.nonBlocking(() -> "x").coalesceBy(same).submit(AppExecutorUtil.getAppExecutorService());
      assertTrue(promise1.isCancelled());
      assertFalse(promise2.isCancelled());
      return promise2;
    });
    String result = waitForPromise(promise);
    assertEquals("x", result);
  }

  public void testDoNotCoalesceDifferent() {
    Pair<CancellablePromise<String>, CancellablePromise<String>> promises = WriteAction.compute(
      () -> Pair.create(ReadAction.nonBlocking(() -> "x").coalesceBy(new Object()).submit(AppExecutorUtil.getAppExecutorService()),
                        ReadAction.nonBlocking(() -> "y").coalesceBy(new Object()).submit(AppExecutorUtil.getAppExecutorService())));
    assertEquals("x", waitForPromise(promises.first));
    assertEquals("y", waitForPromise(promises.second));
  }

  public void testDoNotBlockExecutorThreadWhileWaitingForEdtFinish() throws Exception {
    Semaphore semaphore = new Semaphore(1);
    ExecutorService executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(StringUtil.capitalize(getName()));
    CancellablePromise<Void> promise = ReadAction
      .nonBlocking(() -> {})
      .finishOnUiThread(ModalityState.defaultModalityState(), __ -> semaphore.up())
      .submit(executor);
    assertFalse(semaphore.isUp());
    executor.submit(() -> {}).get(10, TimeUnit.SECONDS); // shouldn't fail by timeout
    waitForPromise(promise);
  }

  public void testStopExecutionWhenOuterProgressIndicatorStopped() {
    ProgressIndicator outerIndicator = new EmptyProgressIndicator();
    CancellablePromise<Object> promise = ReadAction
      .nonBlocking(() -> {
        //noinspection InfiniteLoopStatement
        while (true) {
          ProgressManager.getInstance().getProgressIndicator().checkCanceled();
        }
      })
      .wrapProgress(outerIndicator)
      .submit(AppExecutorUtil.getAppExecutorService());
    outerIndicator.cancel();
    waitForPromise(promise);
  }

  public void testPropagateVisualChangesToOuterIndicator() {
    ProgressIndicator outerIndicator = new ProgressIndicatorBase();
    outerIndicator.setIndeterminate(false);
    waitForPromise(ReadAction
      .nonBlocking(() -> {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        indicator.setText("X");
        indicator.setFraction(0.5);

        assertEquals("X", outerIndicator.getText());
        assertEquals(0.5, outerIndicator.getFraction());

        indicator.setIndeterminate(true);
        assertTrue(outerIndicator.isIndeterminate());
      })
      .wrapProgress(outerIndicator)
      .submit(AppExecutorUtil.getAppExecutorService()));
  }

  public void testDoNotSpawnZillionThreadsForManyCoalescedSubmissions() {
    int count = 1000;

    AtomicInteger executionCount = new AtomicInteger();
    Executor countingExecutor = r -> AppExecutorUtil.getAppExecutorService().execute(() -> {
      executionCount.incrementAndGet();
      r.run();
    });

    List<CancellablePromise<?>> submissions = new ArrayList<>();
    WriteAction.run(() -> {
      for (int i = 0; i < count; i++) {
        submissions.add(ReadAction.nonBlocking(() -> {}).coalesceBy(this).submit(countingExecutor));
      }
    });
    for (CancellablePromise<?> submission : submissions) {
      waitForPromise(submission);
    }

    assertTrue(executionCount.toString(), executionCount.get() <= 2);
  }

  public void testDoNotSubmitToExecutorUntilWriteActionFinishes() {
    AtomicInteger executionCount = new AtomicInteger();
    Executor executor = r -> {
      executionCount.incrementAndGet();
      AppExecutorUtil.getAppExecutorService().execute(r);
    };
    assertEquals("x", waitForPromise(WriteAction.compute(() -> {
      Promise<String> promise = ReadAction.nonBlocking(() -> "x").submit(executor);
      assertEquals(0, executionCount.get());
      return promise;
    })));
    assertEquals(1, executionCount.get());
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testProhibitCoalescingByCommonObjects() {
    NonBlockingReadAction<Void> ra = ReadAction.nonBlocking(() -> {});
    String shouldBeUnique = "Equality should be unique";
    assertThrows(IllegalArgumentException.class, shouldBeUnique, () -> ra.coalesceBy((Object)null));
    assertThrows(IllegalArgumentException.class, null, () -> ra.coalesceBy(this, null));
    assertThrows(IllegalArgumentException.class, shouldBeUnique, () -> ra.coalesceBy(getProject()));
    assertThrows(IllegalArgumentException.class, shouldBeUnique, () -> ra.coalesceBy(new DocumentImpl("")));
    assertThrows(IllegalArgumentException.class, shouldBeUnique, () -> ra.coalesceBy(PsiUtilCore.NULL_PSI_ELEMENT));
    assertThrows(IllegalArgumentException.class, shouldBeUnique, () -> ra.coalesceBy(getClass()));
    assertThrows(IllegalArgumentException.class, shouldBeUnique, () -> ra.coalesceBy(""));
  }

  public void testReportConflictForSameCoalesceFromDifferentPlaces() throws Exception {
    //RC: current implementation treat lambdas from the same class as 'same place' -- i.e. they are OK to use
    // with same .coalesceBy key. Hence the need to create the Inner class here -- to clearly show 'those 2 lambdas
    // are of different origins':

    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      Object same = new Object();
      class Inner {
        void run() {
          ReadAction.nonBlocking(() -> { }).coalesceBy(same).submit(AppExecutorUtil.getAppExecutorService());
        }
      }

      Promise<?> p = WriteAction.compute(() -> {
        Promise<?> p1 = ReadAction.nonBlocking(() -> { }).coalesceBy(same).submit(AppExecutorUtil.getAppExecutorService());
        assertThrows(Throwable.class, "Same coalesceBy arguments", () -> new Inner().run());
        return p1;
      });
      waitForPromise(p);
    });
  }

  public void testDoNotBlockExecutorThreadDuringWriteAction() throws Exception {
    ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("TestDoNotBlockExecutorThreadDuringWriteAction", 1);
    Semaphore mayFinish = new Semaphore();
    Promise<Void> promise = ReadAction.nonBlocking(() -> {
      while (!mayFinish.waitFor(1)) {
        ProgressManager.checkCanceled();
      }
    }).submit(executor);
    for (int i = 0; i < 100; i++) {
      UIUtil.dispatchAllInvocationEvents();
      WriteAction.run(() -> executor.submit(() -> {}).get(1, TimeUnit.SECONDS));
    }
    waitForPromise(promise);
  }

  public void testDoNotLeakFirstCancelledCoalescedAction() {
    Object leak = new Object() {
    };
    Disposable disposable = Disposer.newDisposable();
    Disposer.dispose(disposable);
    try {
      CancellablePromise<String> p = ReadAction
        .nonBlocking(() -> "a")
        .expireWith(disposable)
        .coalesceBy(leak)
        .submit(AppExecutorUtil.getAppExecutorService());
      assertTrue(p.isCancelled());

      LeakHunter.checkLeak(NonBlockingReadActionImpl.getTasksByEquality(), leak.getClass());

      Disposer.disposeChildren(disposable, (child) -> {
        throw new IllegalStateException(child.toString());
      });
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  public void testDoNotLeakSecondCancelledCoalescedAction() throws Exception {
    Disposable disposable = Disposer.newDisposable();
    try {
      Executor executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("TestDoNotLeakSecondCancelledCoalescedAction", 10);

      Object leak = new Object() {
      };
      CancellablePromise<String> p = ReadAction.nonBlocking(() -> "a").coalesceBy(leak).submit(executor);
      WriteAction.run(() -> {
        ReadAction.nonBlocking(() -> "b")
          .coalesceBy(leak)
          .expireWith(disposable)
          .submit(executor)
          .cancel();
      });
      assertTrue(p.isDone());

      ((BoundedTaskExecutor)executor).waitAllTasksExecuted(1, TimeUnit.SECONDS);

      LeakHunter.checkLeak(NonBlockingReadActionImpl.getTasksByEquality(), leak.getClass());

      Disposer.disposeChildren(disposable, (child) -> {
        throw new IllegalStateException(child.toString());
      });
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  public void testDoNotLeakDisposablesOnCancelledIndicator() {
    ProgressIndicator outerIndicator = new EmptyProgressIndicator();
    Disposable disposable = Disposer.newDisposable();
    try {
      Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
        assertThrows(ProcessCanceledException.class, () -> {
          ReadAction.nonBlocking(() -> {
              outerIndicator.cancel();
              throw new ProcessCanceledException();
            })
            .expireWith(disposable)
            .wrapProgress(outerIndicator)
            .executeSynchronously();
        });
      });
      waitForFuture(future);

      Disposer.disposeChildren(disposable, (child) -> {
        throw new IllegalStateException(child.toString());
      });
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  public void testSyncExecutionHonorsConstraints() {
    setupUncommittedDocument();

    AtomicBoolean started = new AtomicBoolean();
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      String s = ReadAction.nonBlocking(() -> {
        started.set(true);
        return "";
      }).withDocumentsCommitted(getProject()).executeSynchronously();
      assertEquals("", s);
    });

    assertFalse(started.get());
    UIUtil.dispatchAllInvocationEvents();

    assertFalse(started.get());
    UIUtil.dispatchAllInvocationEvents();

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    waitForFuture(future);
    assertTrue(started.get());
  }

  private void setupUncommittedDocument() {
    ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(getProject())).disableBackgroundCommit(getTestRootDisposable());
    PsiFile file = createFile("a.txt", "");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> file.getViewProvider().getDocument().insertString(0, "a"));
  }

  private static void waitForFuture(Future<?> future) {
    PlatformTestUtil.waitForFuture(future, 1000);
  }

  public void testSyncExecutionThrowsPCEWhenExpired() {
    Disposable disposable = Disposer.newDisposable();
    Disposer.dispose(disposable);
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      assertThrows(ProcessCanceledException.class, () -> {
        ReadAction.nonBlocking(() -> "").expireWhen(() -> true).executeSynchronously();
      });
      assertThrows(ProcessCanceledException.class, () -> {
        ReadAction.nonBlocking(() -> "").expireWith(disposable).executeSynchronously();
      });
    });
    waitForFuture(future);
  }

  public void testSyncExecutionIsCancellable() {
    AtomicInteger attemptCount = new AtomicInteger();
    int limit = 10;
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      assertEquals("a", ReadAction.nonBlocking(() -> {
        if (attemptCount.incrementAndGet() < limit) {
          //noinspection InfiniteLoopStatement
          while (true) {
            ProgressManager.checkCanceled();
          }
        }
        return "a";
      }).executeSynchronously());
      assertTrue(attemptCount.toString(), attemptCount.get() >= limit);
    });
    while (attemptCount.get() < limit) {
      WriteAction.run(() -> {});
      UIUtil.dispatchAllInvocationEvents();
      TimeoutUtil.sleep(1);
    }
    waitForFuture(future);
  }

  public void testSyncExecutionWorksInsideReadAction() {
    waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ReadAction.run(() -> assertEquals("a", ReadAction.nonBlocking(() -> "a").executeSynchronously()));
    }));
  }

  public void testSyncExecutionFailsInsideReadActionWhenConstraintsAreNotSatisfied() {
    setupUncommittedDocument();
    waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ReadAction.run(() -> {
        assertThrows(IllegalStateException.class, "cannot be satisfied", () -> ReadAction.nonBlocking(() -> "a").withDocumentsCommitted(getProject()).executeSynchronously());
      });
    }));
  }

  public void testSyncExecutionCompletesInsideReadActionWhenWriteActionIsPending() {
    setupUncommittedDocument();
    Semaphore mayStartWrite = new Semaphore(1);
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ReadAction.run(() -> {
        mayStartWrite.up();
        assertEquals("a", ReadAction.nonBlocking(() -> {
          return "a";
        }).executeSynchronously());
      });
    });
    assertTrue(mayStartWrite.waitFor(1000));
    WriteAction.run(() -> {});
    waitForFuture(future);
  }

  public void testSyncExecutionThrowsPCEWhenOuterIndicatorIsCanceled() {
    ProgressIndicatorBase outer = new ProgressIndicatorBase();
    waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ProgressManager.getInstance().runProcess(() -> {
        assertThrows(ProcessCanceledException.class, () -> {
          ReadAction.nonBlocking(() -> {
            outer.cancel();
            ProgressManager.checkCanceled();
          }).executeSynchronously();
        });
      }, outer);
    }));
  }

  public void testCancellationPerformance() {
    Benchmark.newBenchmark("NBRA cancellation", () -> {
      WriteAction.run(() -> {
        for (int i = 0; i < 100_000; i++) {
          ReadAction.nonBlocking(() -> {}).coalesceBy(this).submit(AppExecutorUtil.getAppExecutorService()).cancel();
        }
      });
    }).start();
  }

  public void testExceptionInsideAsyncComputationIsLogged() throws Exception {
    BoundedTaskExecutor executor = (BoundedTaskExecutor)AppExecutorUtil.createBoundedApplicationPoolExecutor("TestExceptionInsideAsyncComputationIsLogged", 10);
    Callable<Object> throwUOE = () -> {
      throw new UnsupportedOperationException();
    };

    watchLoggedExceptions(loggedError -> {
      CancellablePromise<Object> promise = ReadAction.nonBlocking(throwUOE).submit(executor);
      assertLogsAndThrowsUOE(promise, loggedError, executor);

      promise = ReadAction.nonBlocking(throwUOE).submit(executor);
      promise.onProcessed(__ -> {});
      assertLogsAndThrowsUOE(promise, loggedError, executor);

      promise = ReadAction.nonBlocking(throwUOE).submit(executor).onProcessed(__ -> {});
      assertLogsAndThrowsUOE(promise, loggedError, executor);

      promise = ReadAction.nonBlocking(throwUOE).submit(AppExecutorUtil.getAppExecutorService());
      promise.onError(__ -> {});
      assertLogsAndThrowsUOE(promise, loggedError, executor);

      promise = ReadAction.nonBlocking(throwUOE).submit(AppExecutorUtil.getAppExecutorService()).onError(__ -> {});
      assertLogsAndThrowsUOE(promise, loggedError, executor);
    });
  }

  public void testDoNotLogSyncExceptions() {
    watchLoggedExceptions(loggedError -> {
      // unchecked is rethrown
      assertThrows(UnsupportedOperationException.class, () -> ReadAction.nonBlocking(() -> {
        throw new UnsupportedOperationException();
      }).executeSynchronously());
      assertNull(loggedError.get());

      // checked is wrapped
      assertThrows(ExecutionException.class, () -> ReadAction.nonBlocking(() -> {
        throw new IOException();
      }).executeSynchronously());
      assertNull(loggedError.get());
    });
  }

  private static void watchLoggedExceptions(Consumer<? super AtomicReference<Throwable>> runnable) {
    AtomicReference<Throwable> loggedError = new AtomicReference<>();
    LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
      @Override
      public @NotNull Set<Action> processError(@NotNull String category, @NotNull String message, String @NotNull [] details, @Nullable Throwable t) {
        assertNotNull(t);
        loggedError.set(t);
        return Action.NONE;
      }
    }, ()->runnable.accept(loggedError));
  }

  private static void assertLogsAndThrowsUOE(CancellablePromise<Object> promise, AtomicReference<Throwable> loggedError, BoundedTaskExecutor executor) {
    Throwable cause = null;
    try {
      waitForFuture(promise);
    }
    catch (Throwable e) {
      cause = ExceptionUtil.getRootCause(e);
    }
    assertInstanceOf(cause, UnsupportedOperationException.class);
    try {
      executor.waitAllTasksExecuted(1, TimeUnit.SECONDS);
    }
    catch (ExecutionException | InterruptedException | TimeoutException e) {
      throw new RuntimeException(e);
    }
    assertSame(cause, loggedError.getAndSet(null));
  }

  public void testTryAgainOnServiceNotReadyException() {
    AtomicInteger count = new AtomicInteger();
    Callable<String> computation = () -> {
      if (count.incrementAndGet() < 10) {
        throw new ServiceNotReadyException();
      }
      return "x";
    };

    CancellablePromise<String> future1 = ReadAction.nonBlocking(computation).submit(AppExecutorUtil.getAppExecutorService());
    assertEquals("x", PlatformTestUtil.waitForFuture(future1, 1000));
    assertEquals(10, count.get());

    count.set(0);
    Future<String> future2 = ApplicationManager.getApplication().executeOnPooledThread(
      () -> ReadAction.nonBlocking(computation).executeSynchronously());
    assertEquals("x", PlatformTestUtil.waitForFuture(future2, 1000));
    assertEquals(10, count.get());
  }

  public void testReportTooManyUnboundedCalls() throws Exception {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      assertThrows(Throwable.class, SubmissionTracker.ARE_CURRENTLY_ACTIVE, () -> {
        WriteAction.run(() -> {
          for (int i = 0; i < 1000; i++) {
            ReadAction.nonBlocking(() -> { }).submit(AppExecutorUtil.getAppExecutorService());
          }
        });
      });
    });
  }

  public void test_honor_all_disposables() throws Exception {
    for (int i = 0; i < 2; i++) {
      Disposable[] parents = {Disposer.newDisposable("1"), Disposer.newDisposable("2")};
      Disposer.dispose(parents[i]);
      try {
        NonBlockingReadAction<String> nbra = ReadAction
          .nonBlocking(() -> {
            fail();
            return "a";
          });
        for (Disposable parent : parents) {
          nbra = nbra.expireWith(parent);
        }
        CancellablePromise<String> promise = nbra.submit(AppExecutorUtil.getAppExecutorService());
        assertTrue(promise.isCancelled());
        assertNull(promise.get());
      }
      finally {
        Disposer.dispose(parents[1 - i]);
      }
    }
  }

  public void test_submit_doesNot_fail_without_readAction_when_parent_isDisposed() {
    ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(StringUtil.capitalize(getName()), 10);

    for (int i = 0; i < 50; i++) {
      List<Disposable> parents = IntStreamEx.range(100).mapToObj(__ -> Disposer.newDisposable()).toList();
      List<Future<?>> futures = new ArrayList<>();
      for (Disposable parent : parents) {
        futures.add(executor.submit(() -> ReadAction.nonBlocking(() -> {}).expireWith(parent).submit(executor).get()));
        futures.add(executor.submit(() -> {
          try {
            ReadAction.nonBlocking(() -> {}).expireWith(parent).executeSynchronously();
          }
          catch (ProcessCanceledException ignore) {
          }
        }));
      }
      parents.forEach(Disposer::dispose);

      futures.forEach(f -> PlatformTestUtil.waitForFuture(f, 50_000));
    }
  }

  public void test_executeSynchronously_doesNot_return_null_with_not_nullable_callable() {
    ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(StringUtil.capitalize(getName()), 10);

    for (int i = 0; i < 50; i++) {
      List<Disposable> disposables = IntStreamEx.range(100).mapToObj(__ -> Disposer.newDisposable()).toList();
      List<Future<?>> futuresList = new ArrayList<>();
      for (Disposable disposable : disposables) {
        futuresList.add(executor.submit(() -> {
          try {
            Boolean value = ReadAction.nonBlocking(() -> {
              return Boolean.TRUE;
            }).expireWith(disposable).executeSynchronously();
            assertNotNull(value);
          }
          catch (ProcessCanceledException e) {
            //valid outcome
          }
        }));
      }
      disposables.forEach(Disposer::dispose);

      futuresList.forEach(f -> PlatformTestUtil.waitForFuture(f, 50_000));
    }
  }

  public void testOurTasksByEqualityMapMustNotLeakItsTaskEvenWhenTheirEqualityObjectsHaveHorribleEqualsHashCode()
    throws ExecutionException, InterruptedException {

    class MyHorribleEquality {
      private static final AtomicInteger screwYouHash = new AtomicInteger();
      @Override
      public int hashCode() {
        return screwYouHash.incrementAndGet();
      }

      @Override
      public boolean equals(Object obj) {
        return false;
      }
    }

    MyHorribleEquality equality = new MyHorribleEquality();
    CancellablePromise<Void> f1 = ReadAction.nonBlocking(() -> {}).coalesceBy(equality).submit(AppExecutorUtil.getAppExecutorService());
    CancellablePromise<Void> f2 = ReadAction.nonBlocking(() -> {}).coalesceBy(equality).submit(AppExecutorUtil.getAppExecutorService());
    f2.get();
    f1.get();
    LeakHunter.checkLeak(NonBlockingReadActionImpl.getTasksByEquality(), MyHorribleEquality.class);
  }
}
