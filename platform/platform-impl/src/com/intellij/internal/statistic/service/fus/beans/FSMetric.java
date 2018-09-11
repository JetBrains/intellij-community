// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.beans;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class FSMetric extends FSContextProvider {
  public String id;
  public int value;

  private FSMetric(@NotNull UsageDescriptor usageDescriptor) {
    this(usageDescriptor.getKey(), usageDescriptor.getValue(), usageDescriptor.getContext());
  }

  private FSMetric(@NotNull String id, int value, @Nullable FUSUsageContext context) {
    super(context);
    this.id = id;
    this.value = value;
  }

  public static FSMetric create(@NotNull UsageDescriptor usageDescriptor) {
    return new FSMetric(usageDescriptor);
  }

  public static FSMetric create(@NotNull String id, int value) {
    return new FSMetric(id, value, null);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FSMetric)) return false;
    FSMetric metric = (FSMetric)o;
    return value == metric.value &&
           Objects.equals(id, metric.id) && Objects.equals(context, metric.context);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, value);
  }
}
