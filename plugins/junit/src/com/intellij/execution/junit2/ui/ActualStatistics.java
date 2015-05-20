/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.junit2.ui;

import com.intellij.execution.junit2.states.CumulativeStatistics;
import com.intellij.execution.junit2.states.Statistics;
import com.intellij.openapi.util.text.StringUtil;

class ActualStatistics implements TestStatistics {
  private final CumulativeStatistics myStatistics = new CumulativeStatistics();
  private String myPrefix = "";

  public ActualStatistics(final Statistics statistics) {
    myStatistics.add(statistics);
  }

  public void setRunning() {
    myPrefix = TestStatistics.RUNNING_SUITE_PREFIX;
  }

  public String getTime() {
    return myPrefix + StringUtil.formatDuration(myStatistics.getTime());
  }

  public String getMemoryUsageDelta() {
    return showMemory(myStatistics.getMemoryUsage());
  }

  public String getBeforeMemory() {
    return showMemory(myStatistics.getBeforeMemory());
  }

  public String getAfterMemory() {
    return showMemory(myStatistics.getAfterMemory());
  }

  private String showMemory(final long memoryUsage) {
    return myPrefix + Formatters.printFullKBMemory(memoryUsage);
  }
}
