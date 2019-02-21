// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.beans;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class UsageDescriptor {
  private final String myKey;
  private final int myValue;
  private final @Nullable FeatureUsageData myData;

  public UsageDescriptor(@NotNull String key) {
    this(key, 1);
  }

  public UsageDescriptor(@NotNull String key, int value) {
    this(key, value, (FUSUsageContext)null);
  }

  @Deprecated
  public UsageDescriptor(@NotNull String key, int value, @NotNull String... contextData) {
    this(key, value, contextData.length > 0 ? FUSUsageContext.create(contextData) : null);
  }

  @Deprecated
  public UsageDescriptor(@NotNull String key, int value, @Nullable FUSUsageContext context) {
    myKey = ConvertUsagesUtil.ensureProperKey(key);
    myValue = value;
    myData = context != null ? new FeatureUsageData().addFeatureContext(context) : null;
  }

  public UsageDescriptor(@NotNull String key, @Nullable FeatureUsageData data) {
    this(key, 1, data);
  }

  public UsageDescriptor(@NotNull String key, int value, @Nullable FeatureUsageData data) {
    myKey = ConvertUsagesUtil.ensureProperKey(key);
    myValue = value;
    myData = data;
  }

  public String getKey() {
    return myKey;
  }

  public int getValue() {
    return myValue;
  }

  @Nullable
  public FeatureUsageData getData() {
    return myData;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UsageDescriptor that = (UsageDescriptor)o;
    return myValue == that.myValue &&
           Objects.equals(myKey, that.myKey) &&
           Objects.equals(myData, that.myData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myKey, myValue, myData);
  }

  @Override
  public String toString() {
    return myKey + "=" + myValue;
  }
}