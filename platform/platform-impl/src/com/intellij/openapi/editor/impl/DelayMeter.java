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
class DelayMeter {
  private boolean myIsStarted;

  private long myStart;

  private SummaryStatistics myStats = new SummaryStatistics();

  void registerStart() {
    myIsStarted = true;
    myStart = System.nanoTime();
  }

  void registerFinish() {
    if (myIsStarted) {
      long elapsed = System.nanoTime() - myStart;
      myStats.accept(elapsed);

      myIsStarted = false;
    }
  }

  void reset() {
    myStats = new SummaryStatistics();
  }

  double getMin() {
    return myStats.getMin() / 1000000.0D;
  }

  double getMax() {
    return myStats.getMax() / 1000000.0D;
  }

  double getMean() {
    return myStats.getMean() / 1000000.0D;
  }

  double getStandardDeviation() {
    return myStats.getStandardDeviation() / 1000000.0D;
  }


  private static class SummaryStatistics {
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
      } else {
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
}

