/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.util.concurrent.atomic.AtomicInteger;

public class LocalTimeCounter {
  /**
   * VirtualFile.modificationStamp is kept modulo this mask, and is compared with other stamps. Let's avoid accidental stamp inequalities 
   * by normalizing all of them. 
   */
  public static final int TIME_MASK = 0x00ffffff;
  private static final AtomicInteger ourCurrentTime = new AtomicInteger();

  public static long currentTime() {
    return TIME_MASK & ourCurrentTime.incrementAndGet();
  }
}