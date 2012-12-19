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

package com.intellij.execution.junit2.states;

import com.intellij.execution.junit2.segments.ObjectReader;

public class Statistics {
  public int myTime = 0;
  protected long myBeforeMemory = 0;
  protected long myAfterMemory = 0;

  public Statistics(final ObjectReader reader) {
    myTime = reader.readInt();
    myBeforeMemory = reader.readLong();
    myAfterMemory = reader.readLong();
  }

  public Statistics() {
  }

  public int getTime() {
    return myTime;
  }

  public long getBeforeMemory() {
    return myBeforeMemory;
  }

  public long getAfterMemory() {
    return myAfterMemory;
  }

  public long getMemoryUsage() {
    return myAfterMemory - myBeforeMemory;
  }
}
