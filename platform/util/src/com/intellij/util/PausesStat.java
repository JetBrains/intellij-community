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
  private final TIntArrayList pauses = new TIntArrayList();
  private final long epochStart;
  @NotNull private final String myName;
  private volatile boolean started;
  private int maxDuration;
  private Object maxDurationDescription;
  private int totalNumberRecorded;

  public PausesStat(@NotNull String name) {
    myName = name;
    epochStart = System.currentTimeMillis();
  }

  private int register() {
    int stamp = (int)(System.currentTimeMillis() - epochStart);
    pauses.add(stamp);
    return stamp;
  }

  public void started() {
    assert !started;
    if (pauses.size() > N_MAX) {
      // guard against OOME
      int toDelete = N_MAX / 4;
      assert toDelete % 2 == 0 : toDelete;
      pauses.remove(0, toDelete);
      maxDuration = 0;
    }
    register();
    started = true;
    totalNumberRecorded++;
  }

  public void finished(@NotNull String description) {
    assert started;
    int startStamp = pauses.get(pauses.size() - 1);
    int finishStamp = register();
    int duration = finishStamp - startStamp;
    started = false;
    if (duration > maxDuration) {
      maxDuration = duration;
      maxDurationDescription = description;
    }
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

    return myName + " Statistics:" +
           "\nTotal number:     " + number + (totalNumberRecorded == number ? "" : " (Total number recorded: "+totalNumberRecorded+")") +
           "\nTotal time spent: " + total + "ms" +
           "\nAverage duration: " + (number == 0 ? 0 : total / number) + "ms" +
           "\nMedian  duration: " + ArrayUtil.averageAmongMedians(duration, 3) + "ms" +
           "\nMax  duration:    " + maxDuration + "ms (it was '"+maxDurationDescription+"')";
  }
}
