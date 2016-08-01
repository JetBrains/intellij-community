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

import com.intellij.util.containers.UnsignedShortArrayList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class PausesStat {
  private static final int N_MAX = 100000;
  // stores durations of the event: (timestamp of the event end) - (timestamp of the event start) in milliseconds.
  private final UnsignedShortArrayList durations = new UnsignedShortArrayList();
  @NotNull private final String myName;
  private final Thread myEdtThread;
  private boolean started;
  private long startTimeStamp;
  private int maxDuration;
  private Object maxDurationDescription;
  private int totalNumberRecorded;
  private int indexToOverwrite; // used when pauses.size() == N_MAX and we have to overflow cyclically

  public PausesStat(@NotNull String name) {
    myName = name;
    assert EventQueue.isDispatchThread() : Thread.currentThread();
    myEdtThread = Thread.currentThread();
  }

  private int register(int duration) {
    if (durations.size() == N_MAX) {
      durations.set(indexToOverwrite, duration);
      indexToOverwrite = (indexToOverwrite + 1) % N_MAX;
    }
    else {
      durations.add(duration);
    }
    return duration;
  }

  public void started() {
    assertEdt();
    assert !started;
    started = true;
    startTimeStamp = System.currentTimeMillis();
  }

  private void assertEdt() {
    assert Thread.currentThread() == myEdtThread : Thread.currentThread();
  }

  public void finished(@NotNull String description) {
    assertEdt();
    assert started;
    long finishStamp = System.currentTimeMillis();
    int duration = (int)(finishStamp - startTimeStamp);
    started = false;
    duration = Math.min(duration, (1 << 16) - 1);
    if (duration > maxDuration) {
      maxDuration = duration;
      maxDurationDescription = description;
    }
    totalNumberRecorded++;
    register(duration);
  }

  public String statistics() {
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
           "\nMax  duration:    " + (maxDuration == 65535 ? ">" : "") + maxDuration+ "ms (it was '"+maxDurationDescription+"')";
  }
}
