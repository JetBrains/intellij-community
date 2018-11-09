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
package com.intellij.testFramework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.util.concurrency.Semaphore;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Predicate;

/**
 * A progress indicator that starts throwing {@link ProcessCanceledException} after n {@link #checkCanceled()} attempts, where
 * n is specified in the constructor.
 *
 * @author peter
 */
public class BombedProgressIndicator extends AbstractProgressIndicatorBase {
  private final Predicate<StackTraceElement[]> myStackCondition;
  private int myRemainingChecks;
  private volatile Thread myThread;

  public BombedProgressIndicator(int checkCanceledCount) {
    myRemainingChecks = checkCanceledCount;
    myStackCondition = null;
  }

  private BombedProgressIndicator(@NotNull Predicate<StackTraceElement[]> stackCondition) {
    myStackCondition = stackCondition;
    myRemainingChecks = -1;
  }

  @Override
  public void checkCanceled() throws ProcessCanceledException {
    if (myThread == Thread.currentThread()) { // to prevent CoreProgressManager future from interfering with its periodical checkCanceled
      if (myStackCondition != null) {
        if (myStackCondition.test(new Throwable().getStackTrace())) {
          cancel();
        }
      } else {
        if (myRemainingChecks > 0) {
          myRemainingChecks--;
        }
        else {
          cancel();
        }
      }
    }
    super.checkCanceled();
  }

  /**
   * @return whether the indicator was canceled during runnable execution.
   */
  public boolean runBombed(final Runnable runnable) {
    myThread = Thread.currentThread();
    final Semaphore canStart = new Semaphore();
    canStart.down();

    final Semaphore finished = new Semaphore();
    finished.down();

    // ProgressManager invokes indicator.checkCanceled only when there's at least one canceled indicator. So we have to create a mock one
    // on an unrelated thread and cancel it immediately.
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final ProgressIndicatorBase mockIndicator = new ProgressIndicatorBase();
      ProgressManager.getInstance().runProcess(() -> {
        mockIndicator.cancel();
        canStart.up();
        finished.waitFor();
        try {
          ProgressManager.checkCanceled();
          TestCase.fail();
        }
        catch (ProcessCanceledException ignored) {
        }
      }, mockIndicator);
    });

    ProgressManager.getInstance().runProcess(() -> {
      canStart.waitFor();
      try {
        runnable.run();
      }
      catch (ProcessCanceledException ignore) {
      }
      finally {
        finished.up();
      }
    }, this);

    try {
      future.get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }

    return isCanceled();
  }

  public static BombedProgressIndicator explodeOnStack(Predicate<StackTraceElement[]> stackCondition) {
    return new BombedProgressIndicator(stackCondition);
  }

  public static BombedProgressIndicator explodeOnStackElement(Predicate<? super StackTraceElement> stackElementCondition) {
    return explodeOnStack(stack -> Arrays.stream(stack).anyMatch(stackElementCondition));
  }
}
