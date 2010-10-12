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
package com.intellij.statistic.beans;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

public class UsageDescriptor implements Comparable<UsageDescriptor> {

  private final GroupDescriptor myGroup;
  private final String myKey;
  private int myValue;

  public UsageDescriptor(@NotNull GroupDescriptor group, @NotNull String key, int value) {
    myGroup = group;
    myKey = key;
    myValue = value;
  }

  public Pair<GroupDescriptor, String> getId() {
    return Pair.create(getGroup(), getKey());
  }

  @NotNull
  public String getKey() {
    return myKey;
  }

  @NotNull
  public GroupDescriptor getGroup() {
    return myGroup;
  }

  public int getValue() {
    return myValue;
  }

  public void setValue(int i) {
    myValue = i;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UsageDescriptor)) return false;

    UsageDescriptor that = (UsageDescriptor)o;

    if (!myGroup.equals(that.myGroup)) return false;
    if (!myKey.equals(that.myKey)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myGroup.hashCode();
    result = 31 * result + myKey.hashCode();
    return result;
  }

  @Override
  public int compareTo(UsageDescriptor ud) {
    final int byGroup = this.getGroup().compareTo(ud.getGroup());

    return byGroup == 0 ? this.getKey().compareTo(ud.myKey) : byGroup;
  }
}
