/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

@Deprecated // to be removed in 2018.2
public class GroupDescriptor {
  public static final double DEFAULT_PRIORITY = 0.0;
  public static final double HIGHER_PRIORITY = 100.0;
  public static final double LOWER_PRIORITY = -100.0;

  private final String myId;
  private final double myPriority;

  public static GroupDescriptor create(String id) {
    return new GroupDescriptor(id, DEFAULT_PRIORITY);
  }

  public static GroupDescriptor create(String id, double priority) {
    return new GroupDescriptor(id, priority);
  }

  private GroupDescriptor(String id, double priority) {
    myId = ConvertUsagesUtil.ensureProperKey(id);
    myPriority = priority;
  }
  public String getId() {
    return myId;
  }

  public double getPriority() {
    return myPriority;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof GroupDescriptor && myId.equals(((GroupDescriptor)o).myId);
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }
}