// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.beans;

import org.jetbrains.annotations.NotNull;

public final class UsageDescriptor implements Comparable<UsageDescriptor> {
  private final String myKey;
  private final int myValue;

  public UsageDescriptor(@NotNull String key, int value) {
    myKey = ConvertUsagesUtil.ensureProperKey(key);
    myValue = value;
  }

  public UsageDescriptor(@NotNull String key) {
    this(key, 1);
  }

  public String getKey() {
    return myKey;
  }

  public int getValue() {
    return myValue;
  }

  @Override
  public int compareTo(UsageDescriptor ud) {
    return getKey().compareTo(ud.myKey);
  }

  @Override
  public String toString() {
    return myKey + "=" + myValue;
  }
}
