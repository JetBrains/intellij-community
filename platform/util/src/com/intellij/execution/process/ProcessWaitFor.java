/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.process;

import com.intellij.execution.TaskExecutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

public class ProcessWaitFor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.ProcessWaitFor");

  private final Future<?> myWaitForThreadFuture;
  private final BlockingQueue<Consumer<Integer>> myTerminationCallback = new ArrayBlockingQueue<Consumer<Integer>>(1);
  private volatile boolean myDetached = false;

  /** @deprecated use {@link #ProcessWaitFor(Process, TaskExecutor, String)} instead (to be removed in IDEA 2018) */
  @Deprecated
  public ProcessWaitFor(@NotNull final Process process, @NotNull TaskExecutor executor) {
    this(process, executor, "");
  }

  public ProcessWaitFor(@NotNull final Process process, @NotNull TaskExecutor executor, @NotNull final String presentableName) {
    myWaitForThreadFuture = executor.executeTask(new Runnable() {
      @Override
      public void run() {
        String oldThreadName = null;
        if (!StringUtil.isEmptyOrSpaces(presentableName)) {
          oldThreadName = Thread.currentThread().getName();
          Thread.currentThread().setName("ProcessWaitFor: " + presentableName);
        }
        try {
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
        }
        finally {
          if (oldThreadName != null) {
            Thread.currentThread().setName(oldThreadName);
          }
        }
      }
    });
  }

  public void detach() {
    myDetached = true;
    myWaitForThreadFuture.cancel(true);
  }

  public void setTerminationCallback(@NotNull Consumer<Integer> r) {
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
    catch (CancellationException ignored) { }
    catch (TimeoutException ignored) { }

    return myWaitForThreadFuture.isDone();
  }
}