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
package com.intellij.openapi.editor.impl;

import gnu.trove.TLongArrayList;

/**
 * @author Pavel Fatin
 */
class DelayMeter {
  private final TLongArrayList myStartTimes = new TLongArrayList();

  private SummaryStatistics myStats = new SummaryStatistics();

  void registerStart() {
    myStartTimes.add(System.nanoTime());
  }

  void registerFinish() {
    if (!myStartTimes.isEmpty()) {
      long now = System.nanoTime();

      for (int i = 0; i < myStartTimes.size(); i++) {
        long elapsed = now - myStartTimes.get(i);
        myStats.accept(elapsed);
      }

      myStartTimes.clear();
    }
  }

  void reset() {
    myStats = new SummaryStatistics();
  }

  double getMin() {
    return myStats.getMin();
  }

  double getMax() {
    return myStats.getMax();
  }

  double getMean() {
    return myStats.getMean();
  }

  double getStandardDeviation() {
    return myStats.getStandardDeviation();
  }

  public String stat() {
    return myStats.stat();
  }

}

