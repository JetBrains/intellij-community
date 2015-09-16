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

/**
 * @author Pavel Fatin
 */
class SummaryStatistics {
  private int myCount = 0;

  private double myMin = Double.MAX_VALUE;

  private double myMax = 0.0D;

  private double myMean = 0.0D;

  private double myS = 0.0D;

  void accept(double value) {
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
    return myMin == Double.MAX_VALUE ? 0.0D : myMin;
  }

  double getMax() {
    return myMax;
  }

  double getMean() {
    return myMean;
  }

  double getStandardDeviation() {
    return Math.sqrt(myS / (myCount - 1));
  }
}
