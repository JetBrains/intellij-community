// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.beans;

import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UsageDescriptor implements Comparable<UsageDescriptor> {
  private final String myKey;
  private final int myValue;
  private final @Nullable FUSUsageContext myContext;

  public UsageDescriptor(@NotNull String key) {
    this(key, 1);
  }

  public UsageDescriptor(@NotNull String key, int value) {
    this(key, value, (FUSUsageContext)null);
  }

  public UsageDescriptor(@NotNull String key, int value, @NotNull String... contextData) {
    this(key, value, contextData.length > 0 ? FUSUsageContext.create(contextData) : null);
  }

  public UsageDescriptor(@NotNull String key, int value, @Nullable FUSUsageContext context) {
    myKey = ConvertUsagesUtil.ensureProperKey(key);
    myValue = value;
    myContext = context;
  }

  public String getKey() {
    return myKey;
  }

  public int getValue() {
    return myValue;
  }

  @Nullable
  public FUSUsageContext getContext() {
    return myContext;
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