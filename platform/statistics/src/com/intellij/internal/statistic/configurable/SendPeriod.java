/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.internal.statistic.configurable;

import org.jetbrains.annotations.NonNls;

public enum SendPeriod {
  DAILY("daily", 24L * 60 * 60 * 1000), WEEKLY("weekly", 7L * 24 * 60 * 60 * 1000), MONTHLY("monthly", 30L * 24 * 60 * 60 * 1000);

  @NonNls private final String myName;
  @NonNls private final long myPeriod;

  SendPeriod(String name, long period) {
    myName = name;
    myPeriod = period;
  }

  public String getName() {
    return myName;
  }

  public long getMillis() {
    return myPeriod;
  }
}
