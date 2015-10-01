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

import gnu.trove.TDoubleArrayList;

/**
 * @author Pavel Fatin
 */
class SummaryStatistics {
  private int myCount;

  private double myMin = Double.MAX_VALUE;

  private double myMax;

  private double myMean;

  private double myS;
  private final TDoubleArrayList values = new TDoubleArrayList();

  void accept(double value) {
    values.add(value);
    myCount++;

    myMin = Math.min(myMin, value);

    myMax = Math.max(myMax, value);

    if (myCount == 1) {
      myMean = value;
    }
    else {
      double previousMean = myMean;
      myMean += (value - myMean) / myCount;
      myS += (value - previousMean) * (value - myMean);
    }
  }

  double getMin() {
    return (myMin == Double.MAX_VALUE ? 0.0D : myMin) / 1000000;
  }

  double getMax() {
    return myMax / 1000000;
  }

  double getMean() {
    return myMean / 1000000;
  }

  double getStandardDeviation() {
    return Math.sqrt(myS / (myCount - 1)) / 1000000;
  }

  public String stat() {
    values.sort();
    double median = values.get(values.size() / 2)/1000000;
    return String.format("typing delay, ms: min: %5.1f | max: %5.1f | avg: %5.1f | median: %4.1f",
                         getMin(), getMax(), getMean(), median);
  }
}
