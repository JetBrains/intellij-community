/*
 * Copyright 2010-2011 Bas Leijdekkers
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
package com.siyeh.ig.telemetry;

class InspectionRunTime {

  private final String inspectionName;

  private long totalRunTime = 0L;
  private int runCount = 0;

  public InspectionRunTime(String inspectionName) {
    this.inspectionName = inspectionName;
  }

  public void addRunTime(long runTime) {
    synchronized (this) {
      totalRunTime += runTime;
      runCount++;
    }
  }

  public double getAverageRunTime() {
    synchronized (this) {
      return (double)totalRunTime / (double)runCount;
    }
  }

  public String getInspectionName() {
    return inspectionName;
  }

  public int getRunCount() {
    synchronized (this) {
      return runCount;
    }
  }

  public long getTotalRunTime() {
    synchronized (this) {
      return totalRunTime;
    }
  }
}
