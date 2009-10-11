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
package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.Packet;

public class TestMeter {
  private final long myStartTime;

  private final long myInitialUsedMemory;
  private long myFinalMemory;
  private long myDuration;

  private boolean myIsStopped = false;


  public TestMeter() {
    myStartTime = System.currentTimeMillis();
    myInitialUsedMemory = usedMemory();
  }

  private long usedMemory() {
    Runtime runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }

  public void stop() {
    if (!myIsStopped) {
      myDuration = System.currentTimeMillis() - myStartTime;
      myFinalMemory = usedMemory();
      myIsStopped = true;
    }
  }

  public void writeTo(Packet packet) {
    packet.addLong(myDuration).
        addLong(myInitialUsedMemory).
        addLong(myFinalMemory);
  }
}
