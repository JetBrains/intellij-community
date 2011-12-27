/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.internal.statistic.beans;

public class UsageDescriptor implements Comparable<UsageDescriptor> {

  private final String myKey;
  private int myValue;

  public UsageDescriptor(String key, int value) {
    assert key != null;
    myKey = ConvertUsagesUtil.ensureProperKey(key);
    myValue = value;
  }

  public String getKey() {
    return myKey;
  }

  public int getValue() {
    return myValue;
  }

  public void setValue(int i) {
    myValue = i;
  }

  public int compareTo(UsageDescriptor ud) {
    return this.getKey().compareTo(ud.myKey);
  }
}
