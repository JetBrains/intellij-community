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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.UnsignedShortArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class PausesStat {
  private static final Logger LOG = Logger.getInstance(PausesStat.class);
  private static final int N_MAX = 100000;
  @NotNull private final String myName;
  private boolean started;
  private long startTimeStamp;
  private Thread currentThread;

  // fields below are guarded by `this`

  // stores durations of the event: (timestamp of the event end) - (timestamp of the event start) in milliseconds.
  private final UnsignedShortArrayList durations = new UnsignedShortArrayList();
  private int maxDuration;
  private Object maxDurationDescription;
  private int totalNumberRecorded;
  private int indexToOverwrite; // used when pauses.size() == N_MAX and we have to overflow cyclically

  public PausesStat(@NotNull String name) {
    myName = name;
  }

  private synchronized void register(int duration, @NotNull String description) {
    if (duration > maxDuration) {
      maxDuration = duration;
      maxDurationDescription = description;
    }

    totalNumberRecorded++;
    if (durations.size() == N_MAX) {
      durations.set(indexToOverwrite, duration);
      indexToOverwrite = (indexToOverwrite + 1) % N_MAX;
    }
    else {
      durations.add(duration);
    }
  }

  public void started() {
    LOG.assertTrue(!started);
    LOG.assertTrue(startTimeStamp == 0, startTimeStamp);
    currentThread = Thread.currentThread();
    startTimeStamp = System.nanoTime();
    started = true;
  }

  public void finished(@NotNull String description) {
    LOG.assertTrue(currentThread == Thread.currentThread());
    LOG.assertTrue(started);
    currentThread = null;
    started = false;
    long finishStamp = System.nanoTime();
    long startTimeStamp = this.startTimeStamp;
    int durationMs = (int)TimeUnit.NANOSECONDS.toMillis(finishStamp - startTimeStamp);
    this.startTimeStamp = 0;
    if (finishStamp - startTimeStamp < 0 || durationMs < 0) {
      // sometimes despite all efforts the System.nanoTime() can be non-monotonic
      // ignore
      return;
    }

    durationMs = Math.min(durationMs, Short.MAX_VALUE);
    register(durationMs, description);
  }

  public synchronized String statistics() {
    int number = durations.size();
    int[] duration = durations.toArray();
    int total = 0;
    for (int d : duration) {
      total += d;
    }

    return myName + " Statistics" + (totalNumberRecorded == number ? "" : " ("+totalNumberRecorded+" events was recorded in total, but only last "+number+" are reported here)")+":"+
           "\nEvent number:     " + number +
           "\nTotal time spent: " + total + "ms" +
           "\nAverage duration: " + (number == 0 ? 0 : total / number) + "ms" +
           "\nMedian  duration: " + ArrayUtil.averageAmongMedians(duration, 3) + "ms" +
           "\nMax     duration: " + (maxDuration == 65535 ? ">" : "") + maxDuration+ "ms (it was '"+maxDurationDescription+"')";
  }
}
