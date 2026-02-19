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
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Predicate;

/**
 * A progress indicator that starts throwing {@link ProcessCanceledException} after
 * <ol>
 *   <li>Either N {@link #checkCanceled()} attempts, where N is specified in the constructor</li>
 *   <li>Or by condition on stack frames.</li>
 * </ol>
 */
public class BombedProgressIndicator extends AbstractProgressIndicatorBase {
  private int remainingChecks;
  private final @Nullable Predicate<? super StackTraceElement[]> onlyThrowCancellationIfStackCondition;

  /**
   * Memorize thread in which {@link #runBombed(Runnable)} was called, and only check remainingChecks in this thread,
   * to avoid interference from some periodic {@link #checkCanceled()} from unrelated background threads.
   */
  private volatile Thread onlyThrowCancellationIfInThread;


  public BombedProgressIndicator(int checkCanceledCount) {
    remainingChecks = checkCanceledCount;
    onlyThrowCancellationIfStackCondition = null;
  }

  private BombedProgressIndicator(@NotNull Predicate<? super StackTraceElement[]> stackCondition) {
    onlyThrowCancellationIfStackCondition = stackCondition;
    remainingChecks = -1;
  }

  @Override
  public void checkCanceled() throws ProcessCanceledException {
    if (onlyThrowCancellationIfInThread == Thread.currentThread()) { // to prevent CoreProgressManager future from interfering with its periodical checkCanceled
      if (onlyThrowCancellationIfStackCondition != null) {
        if (onlyThrowCancellationIfStackCondition.test(new Throwable().getStackTrace())) {
          cancel();
        }
      } else {
        if (remainingChecks > 0) {
          remainingChecks--;
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
  public boolean runBombed(@NotNull Runnable runnable) {
    onlyThrowCancellationIfInThread = Thread.currentThread();
    Semaphore canStart = new Semaphore();
    canStart.down();

    Semaphore finished = new Semaphore();
    finished.down();

    // ProgressManager invokes the indicator.checkCanceled() only when there's at least one canceled indicator. So we have to create a
    // mock one on an unrelated thread and cancel it immediately.
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ProgressIndicatorBase mockIndicator = new ProgressIndicatorBase();
      ProgressManager.getInstance().runProcess(() -> {
        mockIndicator.cancel();
        canStart.up();
        finished.waitFor();
        try {
          ProgressManager.checkCanceled();
          TestCase.fail();
        }
        catch (@SuppressWarnings("IncorrectCancellationExceptionHandling") ProcessCanceledException ignored) {
        }
      }, mockIndicator);
    });

    ProgressManager.getInstance().runProcess(() -> {
      canStart.waitFor();
      try {
        runnable.run();
      }
      catch (@SuppressWarnings("IncorrectCancellationExceptionHandling") ProcessCanceledException ignore) {
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

  public static BombedProgressIndicator explodeOnStack(@NotNull Predicate<? super StackTraceElement[]> stackCondition) {
    return new BombedProgressIndicator(stackCondition);
  }

  public static BombedProgressIndicator explodeOnStackElement(@NotNull Predicate<? super StackTraceElement> stackElementCondition) {
    return explodeOnStack(stack -> ContainerUtil.exists(stack, stackElementCondition::test));
  }
}
