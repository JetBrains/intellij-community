// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.execution.TaskExecutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

public final class ProcessWaitFor {
  private static final Logger LOG = Logger.getInstance(ProcessWaitFor.class);

  private final Future<?> myWaitForThreadFuture;
  private final BlockingQueue<Consumer<? super Integer>> myTerminationCallback = new ArrayBlockingQueue<>(1);
  private volatile boolean myDetached;

  public ProcessWaitFor(@NotNull Process process, @NotNull TaskExecutor executor, @NotNull String presentableName) {
    myWaitForThreadFuture = executor.executeTask(() -> {
      String threadName = StringUtil.isEmptyOrSpaces(presentableName) ? Thread.currentThread().getName() : presentableName;
      ConcurrencyUtil.runUnderThreadName(threadName, () -> {
        int exitCode = 0;
        try {
          while (!myDetached) {
            try {
              exitCode = process.waitFor();
              break;
            }
            catch (InterruptedException e) {
              if (!myDetached) {
                LOG.debug(e);
              }
            }
          }
        }
        catch (Throwable e) {
          LOG.error(e);
          throw e;
        }
        finally {
          if (!myDetached) {
            try {
              myTerminationCallback.take().consume(exitCode);
            }
            catch (InterruptedException e) {
              LOG.info(e);
            }
          }
        }
      });
    });
  }

  public void detach() {
    myDetached = true;
    myWaitForThreadFuture.cancel(true);
    setTerminationCallback(ignored -> {});  // in case the process has already finished
  }

  public void setTerminationCallback(@NotNull Consumer<? super Integer> r) {
    myTerminationCallback.offer(r);
  }

  public void waitFor() throws InterruptedException {
    try {
      myWaitForThreadFuture.get();
    }
    catch (CancellationException ignored) { }
    catch (ExecutionException e) {
      LOG.error(e);
    }
  }

  public boolean waitFor(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    try {
      myWaitForThreadFuture.get(timeout, unit);
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
    catch (CancellationException | TimeoutException ignored) { }

    return myWaitForThreadFuture.isDone();
  }
}