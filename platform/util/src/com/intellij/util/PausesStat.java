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

import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

public class PausesStat {
  private static final int N_MAX = 200000;
  // stores pairs of (timestamp of the event start), (timestamp of the event end). Timestamps are stored as diffs between System.currentTimeMillis() and epochStart.
  private final TIntArrayList pauses = new TIntArrayList();
  private final long epochStart;
  @NotNull private final String myName;
  private volatile boolean started;
  private int maxDuration;
  private Object maxDurationDescription;
  private int totalNumberRecorded;
  private int indexToOverwrite; // used when pauses.size() == N_MAX and we have to overflow cyclically

  public PausesStat(@NotNull String name) {
    myName = name;
    epochStart = System.currentTimeMillis();
  }

  private int register() {
    int stamp = (int)(System.currentTimeMillis() - epochStart);
    if (pauses.size()/2 == N_MAX) {
      pauses.set(indexToOverwrite, stamp);
      indexToOverwrite = (indexToOverwrite + 1) % N_MAX;
    }
    else {
      pauses.add(stamp);
    }
    return stamp;
  }

  public void started() {
    assert !started;
    register();
    started = true;
  }

  public void finished(@NotNull String description) {
    assert started;
    int startStamp = pauses.get(pauses.size()/2 == N_MAX ? indexToOverwrite-1 : pauses.size() - 1);
    int finishStamp = register();
    int duration = finishStamp - startStamp;
    started = false;
    if (duration > maxDuration) {
      maxDuration = duration;
      maxDurationDescription = description;
    }
    totalNumberRecorded++;
  }

  public String statistics() {
    int total = 0;
    int number = pauses.size() / 2;
    int[] duration = new int[number];
    for (int i = 0; i < number*2; i+=2) {
      int start = pauses.get(i);
      int finish = pauses.get(i+1);
      int thisDuration = finish - start;
      total += thisDuration;
      duration[i / 2] = thisDuration;
    }

    return myName + " Statistics" + (totalNumberRecorded == number ? "" : " ("+totalNumberRecorded+" events was recorded in total, but only last "+number+" are reported here)")+":"+
           "\nEvent number:     " + number +
           "\nTotal time spent: " + total + "ms" +
           "\nAverage duration: " + (number == 0 ? 0 : total / number) + "ms" +
           "\nMedian  duration: " + ArrayUtil.averageAmongMedians(duration, 3) + "ms" +
           "\nMax  duration:    " + maxDuration + "ms (it was '"+maxDurationDescription+"')";
  }
}
