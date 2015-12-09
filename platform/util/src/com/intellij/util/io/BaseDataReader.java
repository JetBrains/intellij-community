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
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author Konstantin Kolosovsky.
 */
public abstract class BaseDataReader {
  private static final Logger LOG = Logger.getInstance(BaseDataReader.class);

  protected final SleepingPolicy mySleepingPolicy;
  protected volatile boolean isStopped;

  private Future<?> myFinishedFuture;

  public BaseDataReader(SleepingPolicy sleepingPolicy) {
    mySleepingPolicy = sleepingPolicy != null ? sleepingPolicy: SleepingPolicy.SIMPLE;
  }

  /** @deprecated use {@link #start(String)} instead (to be removed in IDEA 17) */
  @Deprecated
  protected void start() {
    start("");
  }

  protected void start(@NotNull final String presentableName) {
    if (StringUtil.isEmptyOrSpaces(presentableName)) {
      LOG.warn(new Throwable("Must provide not-empty presentable name"));
    }
    if (myFinishedFuture == null) {
      myFinishedFuture = executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          String oldThreadName = Thread.currentThread().getName();
          if (!StringUtil.isEmptyOrSpaces(presentableName)) {
            Thread.currentThread().setName(StringUtil.first("BaseDataReader: " + presentableName, 120, true));
          }
          try {
            doRun();
          }
          finally {
            Thread.currentThread().setName(oldThreadName);
          }
        }
      });
    }
  }

  @NotNull
  protected abstract Future<?> executeOnPooledThread(@NotNull Runnable runnable);

  public interface SleepingPolicy {
    int sleepTimeWhenWasActive = 1;
    int sleepTimeWhenIdle = 5;

    SleepingPolicy SIMPLE = new SleepingPolicy() {
      @Override
      public int getTimeToSleep(boolean wasActive) {
        return wasActive ? sleepTimeWhenWasActive : sleepTimeWhenIdle;
      }
    };

    SleepingPolicy BLOCKING = new SleepingPolicy() {
      @Override
      public int getTimeToSleep(boolean wasActive) {
        // in blocking mode we need to sleep only when we have reached end of the stream
        // so it can be a long sleeping
        return 50;
      }
    };


    int getTimeToSleep(boolean wasActive);
  }

  public static class AdaptiveSleepingPolicy implements SleepingPolicy {
    private static final int maxSleepTimeWhenIdle = 200;
    private static final int maxIterationsWithCurrentSleepTime = 50;

    private volatile int myIterationsWithCurrentTime;
    private volatile int myCurrentSleepTime = sleepTimeWhenIdle;

    @Override
    public int getTimeToSleep(boolean wasActive) {
      int currentSleepTime = myCurrentSleepTime; // volatile read
      if (wasActive) currentSleepTime = sleepTimeWhenWasActive;
      else if (currentSleepTime == sleepTimeWhenWasActive) {
        currentSleepTime = sleepTimeWhenIdle;
        myIterationsWithCurrentTime = 0;
      }
      else {
        int iterationsWithCurrentTime = ++myIterationsWithCurrentTime;
        if (iterationsWithCurrentTime >= maxIterationsWithCurrentSleepTime) {
          myIterationsWithCurrentTime = 0;
          currentSleepTime = Math.min(2* currentSleepTime, maxSleepTimeWhenIdle);
        }
      }

      myCurrentSleepTime = currentSleepTime; // volatile write
      return currentSleepTime;
    }
  }

  protected void doRun() {
    try {
      while (true) {
        boolean read = readAvailable();

        if (isStopped) {
          break;
        }

        TimeoutUtil.sleep(mySleepingPolicy.getTimeToSleep(read));
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    finally {
      try {
        close();
      }
      catch (IOException e) {
        LOG.error("Can't close stream", e);
      }
    }
  }

  protected abstract boolean readAvailable() throws IOException;
  protected abstract void close() throws IOException;

  public void stop() {
    isStopped = true;
  }

  public void waitFor() throws InterruptedException {
    try {
      myFinishedFuture.get();
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
  }
}