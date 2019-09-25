// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.beans;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @deprecated use {@link MetricEvent}
 */
@Deprecated // to be removed in 2020.2
@ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
public final class UsageDescriptor {
  private final String myKey;
  private final int myValue;
  private final @NotNull FeatureUsageData myData;

  public UsageDescriptor(@NotNull String key) {
    this(key, 1);
  }

  public UsageDescriptor(@NotNull String key, int value) {
    this(key, value, new FeatureUsageData());
  }

  public UsageDescriptor(@NotNull String key, @Nullable FeatureUsageData data) {
    this(key, 1, data);
  }

  public UsageDescriptor(@NotNull String key, int value, @Nullable FeatureUsageData data) {
    myKey = key;
    myValue = value;
    myData = data == null ? new FeatureUsageData() : data;
  }

  public String getKey() {
    return myKey;
  }

  public int getValue() {
    return myValue;
  }

  @NotNull
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