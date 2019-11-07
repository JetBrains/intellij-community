// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.testFramework.PlatformTestUtil.waitForPromise;

/**
 * @author peter
 */
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
    ExecutorService executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(getName());
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
      .cancelWith(outerIndicator)
      .submit(AppExecutorUtil.getAppExecutorService());
    outerIndicator.cancel();
    waitForPromise(promise);
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
    assertThrows(shouldBeUnique, () -> { ra.coalesceBy((Object)null); });
    assertThrows(shouldBeUnique, () -> { ra.coalesceBy(getProject()); });
    assertThrows(shouldBeUnique, () -> { ra.coalesceBy(new DocumentImpl("")); });
    assertThrows(shouldBeUnique, () -> { ra.coalesceBy(PsiUtilCore.NULL_PSI_ELEMENT); });
    assertThrows(shouldBeUnique, () -> { ra.coalesceBy(getClass()); });
    assertThrows(shouldBeUnique, () -> { ra.coalesceBy(""); });
  }

  public void testReportConflictForSameCoalesceFromDifferentPlaces() {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    Object same = new Object();
    class Inner {
      void run() {
        ReadAction.nonBlocking(() -> {}).coalesceBy(same).submit(AppExecutorUtil.getAppExecutorService());
      }
    }

    Promise<?> p = WriteAction.compute(() -> {
      Promise<?> p1 = ReadAction.nonBlocking(() -> {}).coalesceBy(same).submit(AppExecutorUtil.getAppExecutorService());
      assertThrows("Same coalesceBy arguments", () -> new Inner().run());
      return p1;
    });
    waitForPromise(p);
  }

  private static void assertThrows(String messagePart, Runnable runnable) {
    try {
      runnable.run();
    }
    catch (Throwable e) {
      assertTrue(e.getMessage(), e.getMessage().contains(messagePart));
      return;
    }
    fail();
  }

  public void testDoNotBlockExecutorThreadDuringWriteAction() throws Exception {
    ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("a", 1);
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
    Object leak = new Object() {};
    Disposable disposable = Disposer.newDisposable();
    Disposer.dispose(disposable);
    CancellablePromise<String> p = ReadAction
      .nonBlocking(() -> "a")
      .expireWith(disposable)
      .coalesceBy(leak)
      .submit(AppExecutorUtil.getAppExecutorService());
    assertTrue(p.isCancelled());

    LeakHunter.checkLeak(NonBlockingReadActionImpl.getTasksByEquality(), leak.getClass());
  }

  public void testDoNotLeakSecondCancelledCoalescedAction() throws Exception {
    Executor executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(getName(), 10);

    Object leak = new Object(){};
    CancellablePromise<String> p = ReadAction.nonBlocking(() -> "a").coalesceBy(leak).submit(executor);
    WriteAction.run(() -> {
      ReadAction.nonBlocking(() -> "b").coalesceBy(leak).submit(executor).cancel();
    });
    assertTrue(p.isDone());

    ((BoundedTaskExecutor) executor).waitAllTasksExecuted(1, TimeUnit.SECONDS);

    LeakHunter.checkLeak(NonBlockingReadActionImpl.getTasksByEquality(), leak.getClass());
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
        assertThrows("cannot be satisfied", () -> ReadAction.nonBlocking(() -> "a").withDocumentsCommitted(getProject()).executeSynchronously());
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
}
