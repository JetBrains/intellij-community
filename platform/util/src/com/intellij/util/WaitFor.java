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
package com.intellij.util;

import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;


/**
 * @author kir
 */
public abstract class WaitFor {
  private static final int DEFAULT_STEP = 10;
  private static final int MAX_TIMEOUT = 60 * 1000;

  private long myWaitTime;
  private boolean myInterrupted;
  private volatile boolean myConditionRealized;
  private Future<?> myThread;

  /** Blocking call */
  public WaitFor() {
    this(MAX_TIMEOUT);
  }

  public WaitFor(int timeoutMsecs) {
    this(timeoutMsecs, DEFAULT_STEP);
  }

  /** Blocking call */
  public WaitFor(int timeoutMsecs, final int step) {
    long started = System.currentTimeMillis();
    long deadline = timeoutMsecs == -1 ? Long.MAX_VALUE : started + timeoutMsecs;

    myConditionRealized = false;
    try {
      while(!(myConditionRealized = condition()) && System.currentTimeMillis() < deadline) {
          Thread.sleep(step);
      }
    } catch (InterruptedException e) {
      myInterrupted = true;
    }
    myWaitTime = System.currentTimeMillis() - started;
  }

  /** Non-blocking call */
  public WaitFor(final int timeoutMsecs, final Runnable toRunOnTrue) {
    myThread = AppExecutorUtil.getAppExecutorService().submit(() -> {
      myConditionRealized = new WaitFor(timeoutMsecs) {
        @Override
        protected boolean condition() {
          return WaitFor.this.condition();
        }
      }.isConditionRealized();

      if (myConditionRealized) {
        toRunOnTrue.run();
      }
    });
  }

  public long getWaitedTime() {
    return myWaitTime;
  }

  public boolean isConditionRealized() {
    return myConditionRealized;
  }

  public boolean isInterrupted() {
    return myInterrupted;
  }

  protected abstract boolean condition();

  public void assertCompleted() {
    assertCompleted("");
  }
  public void assertCompleted(String message) {
    assert condition(): message;
  }

  @TestOnly
  public void join() throws InterruptedException, ExecutionException {
    Future<?> thread = myThread;
    if (thread != null) {
      thread.get();
    }
  }
}
