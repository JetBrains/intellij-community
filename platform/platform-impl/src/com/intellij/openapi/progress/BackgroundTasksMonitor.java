/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.progress;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.PlusMinus;

import java.util.HashMap;
import java.util.Map;

/**
 * @author irengrig
 *         Date: 4/13/11
 *         Time: 5:32 PM
 */
public class BackgroundTasksMonitor implements PlusMinus<String> {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.BackgroundTasksMonitor");
  private static final long ourStatInterval = 300000;
  private long myRecentTime;
  private final Map<String, Integer> myMap;
  private final Map<String, Integer> myMaxMap;
  private final Object myLock;
  private final String myQueueTitle;

  public BackgroundTasksMonitor(final String queueTitle) {
    myQueueTitle = queueTitle;
    myMap = new HashMap<String, Integer>();
    myMaxMap = new HashMap<String, Integer>();
    myLock = new Object();
    myRecentTime = 0;
  }

  @Override
  public void plus(String title) {
    synchronized (myLock) {
      final Integer previous = myMap.get(title);
      final int newVal = previous == null ? 1 : (previous + 1);
      myMap.put(title, newVal);
      final Integer max = myMaxMap.get(title);
      if (max == null || max < newVal) {
        myMaxMap.put(title, newVal);
      }
      reportStatistics();
    }
  }


  @Override
  public void minus(String title) {
    synchronized (myLock) {
      final Integer integer = myMap.get(title);
      assert integer != null;
      if (integer == 1) {
        myMap.remove(title);
      } else {
        myMap.put(title, integer - 1);
      }
      reportStatistics();
    }
  }

  private void reportStatistics() {
    final long time = System.currentTimeMillis();
    if (time - ourStatInterval < myRecentTime) return;
    final StringBuilder sb = new StringBuilder("BackgroundTaskQueue '" + myQueueTitle + "' usage statistics\n");
    sb.append("----------------------------------------------------\n");
    sb.append("Current Values:");
    for (Map.Entry<String, Integer> entry : myMap.entrySet()) {
      sb.append(entry.getKey()).append(": ").append(entry.getValue());
    }
    sb.append("\nMaximum Values:");
    for (Map.Entry<String, Integer> entry : myMaxMap.entrySet()) {
      sb.append('\n').append(entry.getKey()).append(": ").append(entry.getValue());
    }
    sb.append("----------------------------------------------------\n");
    LOG.info(sb.toString());
    myRecentTime = time;
  }
}
