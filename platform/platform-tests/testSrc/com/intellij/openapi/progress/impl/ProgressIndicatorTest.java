/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.progress.impl;

import com.intellij.ide.util.DelegatingProgressIndicator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.testFramework.BombedProgressIndicator;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.DoubleArrayList;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author yole
 */
public class ProgressIndicatorTest extends LightPlatformTestCase {
  public ProgressIndicatorTest() {
    PlatformTestCase.autodetectPlatformPrefix();
  }

  public void testCheckCanceledHasStackFrame() {
    ProgressIndicator pib = new ProgressIndicatorBase();
    pib.cancel();
    try {
      pib.checkCanceled();
      fail("Please restore ProgressIndicatorBase.checkCanceled() check!");
    }
    catch(ProcessCanceledException ex) {
      boolean hasStackFrame = ex.getStackTrace().length != 0;
      assertTrue("Should have stackframe", hasStackFrame);
    }
  }

  public void testProgressManagerCheckCanceledWorksRightAfterIndicatorBeenCanceled() {
    for (int i=0; i<1000;i++) {
      final ProgressIndicatorBase indicator = new ProgressIndicatorBase();
      ProgressManager.getInstance().runProcess(new Runnable() {
        @Override
        public void run() {
          ProgressManager.checkCanceled();
          try {
            indicator.cancel();
            ProgressManager.checkCanceled();
            fail("checkCanceled() must have caught just canceled indicator");
          }
          catch (ProcessCanceledException ignored) {
          }
        }
      }, indicator);
    }
  }

  private volatile long prevTime;
  private volatile long now;
  public void testCheckCanceledGranularity() throws InterruptedException {
    prevTime = now = 0;
    final long warmupEnd = System.currentTimeMillis() + 1000;
    final TLongArrayList times = new TLongArrayList();
    final long end = warmupEnd + 1000;

    ApplicationManagerEx.getApplicationEx().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        final Alarm alarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD, getTestRootDisposable());
        ProgressIndicatorEx indicator = (ProgressIndicatorEx)ProgressIndicatorProvider.getGlobalProgressIndicator();
        prevTime = System.currentTimeMillis();
        assert indicator != null;
        indicator.addStateDelegate(new ProgressIndicatorStub() {
          @Override
          public void checkCanceled() throws ProcessCanceledException {
            now = System.currentTimeMillis();
            if (now > warmupEnd) {
              int delta = (int)(now - prevTime);
              times.add(delta);
            }
            prevTime = now;
          }
        });
        while (System.currentTimeMillis() < end) {
          ProgressManager.checkCanceled();
        }
        alarm.cancelAllRequests();
      }
    }, "", false, getProject(), null, "");
    long averageDelay = ArrayUtil.averageAmongMedians(times.toNativeArray(), 5);
    System.out.println("averageDelay = " + averageDelay);
    assertTrue(averageDelay < CoreProgressManager.CHECK_CANCELED_DELAY_MILLIS *3);
  }

  public void testProgressIndicatorUtilsScheduleWithWriteActionPriority() throws Throwable {
    final AtomicBoolean insideReadAction = new AtomicBoolean();
    final ProgressIndicatorBase indicator = new ProgressIndicatorBase();
    ProgressIndicatorUtils.scheduleWithWriteActionPriority(indicator, new ReadTask() {
      @Override
      public void computeInReadAction(@NotNull ProgressIndicator indicator) {
        insideReadAction.set(true);
        while (true) {
          ProgressManager.checkCanceled();
        }
      }

      @Override
      public void onCanceled(@NotNull ProgressIndicator indicator) {
      }
    });
    UIUtil.dispatchAllInvocationEvents();
    while (!insideReadAction.get()) {

    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        assertTrue(indicator.isCanceled());
      }
    });
    assertTrue(indicator.isCanceled());
  }

  public void testThereIsNoDelayBetweenIndicatorCancelAndProgressManagerCheckCanceled() throws Throwable {
    for (int i=0; i<100;i++) {
      final ProgressIndicatorBase indicator = new ProgressIndicatorBase();
      List<Thread> threads = ContainerUtil.map(Collections.nCopies(10, ""), new Function<String, Thread>() {
        @Override
        public Thread fun(String s) {
          return new Thread(new Runnable() {
            @Override
            public void run() {
              ProgressManager.getInstance().executeProcessUnderProgress(new Runnable() {
                @Override
                public void run() {
                  try {
                    Thread.sleep(new Random().nextInt(100));
                    indicator.cancel();
                    ProgressManager.checkCanceled();
                    fail("checkCanceled() must know about canceled indicator even from different thread");
                  }
                  catch (ProcessCanceledException ignored) {
                  }
                  catch (Throwable e) {
                    exception = e;
                  }
                }
              }, indicator);
            }
          },"indicator test"){{start();}};
        }
      });
      ContainerUtil.process(threads, new Processor<Thread>() {
        @Override
        public boolean process(Thread thread) {
          try {
            thread.join();
          }
          catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          return true;
        }
      });
    }
    if (exception != null) throw exception;
  }

  private volatile boolean checkCanceledCalled;
  private volatile boolean taskCanceled;
  private volatile boolean taskSucceeded;
  private volatile Throwable exception;
  public void testProgressManagerCheckCanceledDoesNotDelegateToProgressIndicatorIfThereAreNoCanceledIndicators() throws Throwable {
    final long warmupEnd = System.currentTimeMillis() + 1000;
    final long end = warmupEnd + 10000;
    checkCanceledCalled = false;
    final ProgressIndicatorBase myIndicator = new ProgressIndicatorBase();
    taskCanceled = taskSucceeded = false;
    exception = null;
    Future<?> future = ((ProgressManagerImpl)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(
      new Task.Backgroundable(getProject(), "xxx") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            assertFalse(ApplicationManager.getApplication().isDispatchThread());
            assertSame(indicator, myIndicator);
            while (System.currentTimeMillis() < end) {
              ProgressManager.checkCanceled();
            }
          }
          catch (ProcessCanceledException e) {
            exception = e;
            checkCanceledCalled = true;
            throw e;
          }
          catch (RuntimeException e) {
            exception = e;
            throw e;
          }
          catch (Error e) {
            exception = e;
            throw e;
          }
        }

        @Override
        public void onCancel() {
          taskCanceled = true;
        }

        @Override
        public void onSuccess() {
          taskSucceeded = true;
        }
      }, myIndicator, null);

    ApplicationManager.getApplication().assertIsDispatchThread();

    while (!future.isDone()) {
      if (System.currentTimeMillis() < warmupEnd) {
        assertFalse(checkCanceledCalled);
      }
      else {
        myIndicator.cancel();
      }
    }
    // invokeLater in runProcessWithProgressAsynchronously
    UIUtil.dispatchAllInvocationEvents();

    assertTrue(checkCanceledCalled);
    assertFalse(taskSucceeded);
    assertTrue(taskCanceled);
    assertTrue(String.valueOf(exception), exception instanceof ProcessCanceledException);
  }

  private volatile boolean myFlag;
  public void testPerverseIndicator() {
    checkCanceledCalled = false;
    ProgressIndicator indicator = new ProgressIndicatorStub() {
      @Override
      public void checkCanceled() throws ProcessCanceledException {
        checkCanceledCalled = true;
        if (myFlag) throw new ProcessCanceledException();
      }
    };
    ensureCheckCanceledCalled(indicator);
  }

  private void ensureCheckCanceledCalled(@NotNull ProgressIndicator indicator) {
    myFlag = false;
    Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myTestRootDisposable);
    alarm.addRequest(new Runnable() {
      @Override
      public void run() {
        myFlag = true;
      }
    }, 100);
    final long start = System.currentTimeMillis();
    try {
      ProgressManager.getInstance().executeProcessUnderProgress(new Runnable() {
        @Override
        public void run() {
          while (System.currentTimeMillis() - start < 10000) {
            ProgressManager.checkCanceled();
          }
        }
      }, indicator);
      fail("must have thrown PCE");
    }
    catch (ProcessCanceledException e) {
      assertTrue(checkCanceledCalled);
    }
  }

  public void testExtremelyPerverseIndicatorWhichCancelMethodIsNoop() {
    checkCanceledCalled = false;
    ProgressIndicator indicator = new ProgressIndicatorStub() {
      @Override
      public void checkCanceled() throws ProcessCanceledException {
        checkCanceledCalled = true;
        if (myFlag) throw new ProcessCanceledException();
      }

      @Override
      public void cancel() {
      }
    };
    ensureCheckCanceledCalled(indicator);
  }

  public void testNestedIndicatorsAreCanceledRight() {
    checkCanceledCalled = false;
    ProgressManager.getInstance().executeProcessUnderProgress(new Runnable() {
      @Override
      public void run() {
        assertFalse(CoreProgressManager.threadsUnderCanceledIndicator.contains(Thread.currentThread()));
        ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
        assertTrue(indicator != null && !indicator.isCanceled());
        indicator.cancel();
        assertTrue(CoreProgressManager.threadsUnderCanceledIndicator.contains(Thread.currentThread()));
        assertTrue(indicator.isCanceled());
        final ProgressIndicatorEx nested = new ProgressIndicatorBase();
        nested.addStateDelegate(new ProgressIndicatorStub() {
          @Override
          public void checkCanceled() throws ProcessCanceledException {
            checkCanceledCalled = true;
          }
        });
        ProgressManager.getInstance().executeProcessUnderProgress(new Runnable() {
          @Override
          public void run() {
            assertFalse(CoreProgressManager.threadsUnderCanceledIndicator.contains(Thread.currentThread()));
            ProgressIndicator indicator2 = ProgressIndicatorProvider.getGlobalProgressIndicator();
            assertTrue(indicator2 != null && !indicator2.isCanceled());
            assertSame(indicator2, nested);
            ProgressManager.checkCanceled();
          }
        }, nested);

        ProgressIndicator indicator3 = ProgressIndicatorProvider.getGlobalProgressIndicator();
        assertSame(indicator, indicator3);

        assertTrue(CoreProgressManager.threadsUnderCanceledIndicator.contains(Thread.currentThread()));
      }
    }, new EmptyProgressIndicator());
    assertFalse(checkCanceledCalled);
  }

  public void testWrappedIndicatorsAreSortedRight() {
    EmptyProgressIndicator indicator1 = new EmptyProgressIndicator();
    DelegatingProgressIndicator indicator2 = new DelegatingProgressIndicator(indicator1);
    final DelegatingProgressIndicator indicator3 = new DelegatingProgressIndicator(indicator2);
    ProgressManager.getInstance().executeProcessUnderProgress(new Runnable() {
      @Override
      public void run() {
        ProgressIndicator current = ProgressIndicatorProvider.getGlobalProgressIndicator();
        assertSame(indicator3, current);
      }
    }, indicator3);
    assertFalse(checkCanceledCalled);
  }

  public void testProgressPerformance() {
    PlatformTestUtil.startPerformanceTest("progress", 100, new ThrowableRunnable() {
      @Override
      public void run() throws Throwable {
        EmptyProgressIndicator indicator = new EmptyProgressIndicator();
        for (int i=0;i<100000;i++) {
          ProgressManager.getInstance().executeProcessUnderProgress(EmptyRunnable.getInstance(), indicator);
        }
      }
    }).assertTiming();
  }

  public void testWrapperIndicatorGotCanceledTooWhenInnerIndicatorHas() {
    final ProgressIndicator progress = new ProgressIndicatorBase(){
      @Override
      protected boolean isCancelable() {
        return true;
      }
    };
    try {
      ProgressManager.getInstance().executeProcessUnderProgress(new Runnable() {
        @Override
        public void run() {
          assertFalse(CoreProgressManager.threadsUnderCanceledIndicator.contains(Thread.currentThread()));
          assertTrue(!progress.isCanceled());
          progress.cancel();
          assertTrue(CoreProgressManager.threadsUnderCanceledIndicator.contains(Thread.currentThread()));
          assertTrue(progress.isCanceled());
          while (true) { // wait for PCE
            ProgressManager.checkCanceled();
          }
        }
      }, ProgressWrapper.wrap(progress));
      fail("PCE must have been thrown");
    }
    catch (ProcessCanceledException ignored) {

    }
  }

  public void testBombedIndicator() {
    final int count = 10;
    new BombedProgressIndicator(count).runBombed(new Runnable() {
      @Override
      public void run() {
        for (int i = 0; i < count * 2; i++) {
          TimeoutUtil.sleep(10);
          try {
            ProgressManager.checkCanceled();
            if (i >= count) {
              ProgressManager.checkCanceled();
              fail("PCE expected on " + i + "th check");
            }
          }
          catch (ProcessCanceledException e) {
            if (i < count) {
              fail("Too early PCE");
            }
          }
        }
      }
    });
  }

  private static class ProgressIndicatorStub implements ProgressIndicatorEx {
    private volatile boolean myCanceled;

    @Override
    public void addStateDelegate(@NotNull ProgressIndicatorEx delegate) {
      throw new RuntimeException();
    }

    @Override
    public boolean isModalityEntered() {
      throw new RuntimeException();
    }

    @Override
    public void finish(@NotNull TaskInfo task) {
    }

    @Override
    public boolean isFinished(@NotNull TaskInfo task) {
      throw new RuntimeException();
    }

    @Override
    public boolean wasStarted() {
      throw new RuntimeException();
    }

    @Override
    public void processFinish() {
      throw new RuntimeException();
    }

    @Override
    public void initStateFrom(@NotNull ProgressIndicator indicator) {
    }

    @NotNull
    @Override
    public Stack<String> getTextStack() {
      throw new RuntimeException();
    }

    @NotNull
    @Override
    public DoubleArrayList getFractionStack() {
      throw new RuntimeException();
    }

    @NotNull
    @Override
    public Stack<String> getText2Stack() {
      throw new RuntimeException();
    }

    @Override
    public int getNonCancelableCount() {
      throw new RuntimeException();
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void setText(String text) {
      throw new RuntimeException();
    }

    @Override
    public String getText() {
      throw new RuntimeException();
    }

    @Override
    public String getText2() {
      throw new RuntimeException();
    }

    @Override
    public void setText2(String text) {
      throw new RuntimeException();
    }

    @Override
    public double getFraction() {
      throw new RuntimeException();
    }

    @Override
    public void setFraction(double fraction) {
      throw new RuntimeException();
    }

    @Override
    public void pushState() {
      throw new RuntimeException();
    }

    @Override
    public void popState() {
      throw new RuntimeException();
    }

    @Override
    public void startNonCancelableSection() {
      throw new RuntimeException();
    }

    @Override
    public void finishNonCancelableSection() {
      throw new RuntimeException();
    }

    @Override
    public boolean isModal() {
      return false;
    }

    @NotNull
    @Override
    public ModalityState getModalityState() {
      throw new RuntimeException();
    }

    @Override
    public void setModalityProgress(ProgressIndicator modalityProgress) {
      throw new RuntimeException();
    }

    @Override
    public boolean isIndeterminate() {
      throw new RuntimeException();
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {
      throw new RuntimeException();
    }

    @Override
    public boolean isPopupWasShown() {
      throw new RuntimeException();
    }

    @Override
    public boolean isShowing() {
      throw new RuntimeException();
    }

    @Override
    public boolean isRunning() {
      return true;
    }

    @Override
    public void cancel() {
      myCanceled = true;
      ProgressManager.canceled(this);
    }

    @Override
    public boolean isCanceled() {
      return myCanceled;
    }

    @Override
    public void checkCanceled() throws ProcessCanceledException {
       if (myCanceled) throw new ProcessCanceledException();
    }
  }
}
