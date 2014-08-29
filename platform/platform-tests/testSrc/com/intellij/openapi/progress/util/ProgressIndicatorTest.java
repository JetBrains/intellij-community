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
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Alarm;
import com.intellij.util.containers.DoubleArrayList;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author yole
 */
public class ProgressIndicatorTest extends LightPlatformTestCase {
  public void testCheckCanceledHasNoStackFrame() {
    ProgressIndicatorBase pib = new ProgressIndicatorBase();
    pib.cancel();
    boolean hadException = false;
    try {
      pib.checkCanceled();
    }
    catch(ProcessCanceledException ex) {
      hadException = true;
      assertTrue("Should have no stackframe", ex.getStackTrace().length == 0);
    }
    assertTrue("Please restore ProgressIndicatorBase.checkCanceled() check!", hadException);
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
    final AtomicBoolean run = new AtomicBoolean(true);
    final AtomicBoolean insideReadAction = new AtomicBoolean();
    final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
    final ProgressIndicatorBase indicator = new ProgressIndicatorBase();
    ProgressIndicatorUtils.scheduleWithWriteActionPriority(indicator, new ReadTask() {
      @Override
      public void computeInReadAction(@NotNull ProgressIndicator indicator) {
        insideReadAction.set(true);
        while (run.get()) {
          ProgressManager.checkCanceled();
        }
      }

      @Override
      public void onCanceled(@NotNull ProgressIndicator indicator) {
        try {
          assertTrue(run.get()); // cancel should happen early
          run.set(false);
        }
        catch (Throwable e) {
          exception.set(e);
        }
      }
    });
    UIUtil.dispatchAllInvocationEvents();
    while (!insideReadAction.get()) {
      ;
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          assertTrue(indicator.isCanceled());
        }
        catch (Throwable e) {
          exception.set(e);
        }
      }
    });
    assertFalse(run.get());
    if (exception.get() != null) throw exception.get();
  }

  private static class ProgressIndicatorStub implements ProgressIndicatorEx {
    @Override
    public void addStateDelegate(@NotNull ProgressIndicatorEx delegate) {

    }

    @Override
    public boolean isModalityEntered() {
      return false;
    }

    @Override
    public void finish(@NotNull TaskInfo task) {

    }

    @Override
    public boolean isFinished(@NotNull TaskInfo task) {
      return false;
    }

    @Override
    public boolean wasStarted() {
      return false;
    }

    @Override
    public void processFinish() {

    }

    @Override
    public void initStateFrom(@NotNull ProgressIndicator indicator) {

    }

    @NotNull
    @Override
    public Stack<String> getTextStack() {
      return null;
    }

    @NotNull
    @Override
    public DoubleArrayList getFractionStack() {
      return null;
    }

    @NotNull
    @Override
    public Stack<String> getText2Stack() {
      return null;
    }

    @Override
    public int getNonCancelableCount() {
      return 0;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public boolean isRunning() {
      return false;
    }

    @Override
    public void cancel() {

    }

    @Override
    public boolean isCanceled() {
      return false;
    }

    @Override
    public void setText(String text) {

    }

    @Override
    public String getText() {
      return null;
    }

    @Override
    public void setText2(String text) {

    }

    @Override
    public String getText2() {
      return null;
    }

    @Override
    public double getFraction() {
      return 0;
    }

    @Override
    public void setFraction(double fraction) {

    }

    @Override
    public void pushState() {

    }

    @Override
    public void popState() {

    }

    @Override
    public void startNonCancelableSection() {

    }

    @Override
    public void finishNonCancelableSection() {

    }

    @Override
    public boolean isModal() {
      return false;
    }

    @NotNull
    @Override
    public ModalityState getModalityState() {
      return null;
    }

    @Override
    public void setModalityProgress(ProgressIndicator modalityProgress) {

    }

    @Override
    public boolean isIndeterminate() {
      return false;
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {

    }

    @Override
    public void checkCanceled() throws ProcessCanceledException {

    }

    @Override
    public boolean isPopupWasShown() {
      return false;
    }

    @Override
    public boolean isShowing() {
      return false;
    }
  }
}
