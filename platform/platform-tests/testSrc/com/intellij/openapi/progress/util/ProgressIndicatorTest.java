/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.progress.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import com.intellij.util.Processor;
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
  public void testCheckCanceledHasNoStackFrame() {
    ProgressIndicatorBase pib = new ProgressIndicatorBase();
    pib.cancel();
    try {
      pib.checkCanceled();
      fail("Please restore ProgressIndicatorBase.checkCanceled() check!");
    }
    catch(ProcessCanceledException ex) {
      assertTrue("Should have no stackframe", ApplicationManager.getApplication().isInternal() ? ex.getStackTrace().length != 0 : ex.getStackTrace().length == 0);
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
    long averageDelay = PlatformTestUtil.averageAmongMedians(times.toNativeArray(), 5);
    System.out.println("averageDelay = " + averageDelay);
    assertTrue(averageDelay < ProgressManagerImpl.CHECK_CANCELED_DELAY_MILLIS*3);
  }

  public void testProgressIndicatorUtils() throws Throwable {
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
      ;
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
          }){{start();}};
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
  private volatile boolean taskFinished;
  private volatile Throwable exception;
  public void testProgressManagerCheckCanceledDoesNotDelegateToProgressIndicatorIfThereAreNoCanceledIndicators() throws Throwable {
    final long warmupEnd = System.currentTimeMillis() + 1000;
    final long end = warmupEnd + 1000;
    checkCanceledCalled = false;
    final ProgressIndicator myIndicator = new ProgressIndicatorStub() {
      @Override
      public void checkCanceled() throws ProcessCanceledException {
        checkCanceledCalled = true;
        assertTrue(isCanceled());
        super.checkCanceled();
      }

      @Override
      public void processFinish() {
        taskFinished = true;
      }
    };
    taskCanceled = taskSucceeded = taskFinished = false;
    exception = null;
    Future<?> future = ProgressManagerImpl.runProcessWithProgressAsynchronously(new Task.Backgroundable(getProject(), "xxx") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          assertFalse(ApplicationManager.getApplication().isDispatchThread());
          assertSame(indicator, myIndicator);
          while (System.currentTimeMillis() < end) {
            ProgressManager.checkCanceled();
          }
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
    assertTrue(taskFinished);

    // invokeLater in runProcessWithProgressAsynchronously
    UIUtil.dispatchAllInvocationEvents();

    assertTrue(checkCanceledCalled);
    assertFalse(taskSucceeded);
    assertTrue(taskCanceled);
    assertTrue(String.valueOf(exception), exception instanceof ProcessCanceledException);
  }

  private static class ProgressIndicatorStub extends EmptyProgressIndicator implements ProgressIndicatorEx {
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
  }
}
