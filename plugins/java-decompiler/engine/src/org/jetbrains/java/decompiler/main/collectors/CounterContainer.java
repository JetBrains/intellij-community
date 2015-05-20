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
package org.jetbrains.java.decompiler.main.collectors;

public class CounterContainer {

  public static final int STATEMENT_COUNTER = 0;
  public static final int EXPRENT_COUNTER = 1;
  public static final int VAR_COUNTER = 2;

  private final int[] values = new int[]{1, 1, 1};

  public void setCounter(int counter, int value) {
    values[counter] = value;
  }

  public int getCounter(int counter) {
    return values[counter];
  }

  public int getCounterAndIncrement(int counter) {
    return values[counter]++;
  }
}
