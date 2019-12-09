// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.beans.MetricEventFactoryKt;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @see ProjectUsagesCollector
 */
public abstract class ApplicationUsagesCollector extends FeatureUsagesCollector {
  private static final ExtensionPointName<ApplicationUsagesCollector> EP_NAME =
    ExtensionPointName.create("com.intellij.statistics.applicationUsagesCollector");

  @NotNull
  public static Set<ApplicationUsagesCollector> getExtensions(@NotNull UsagesCollectorConsumer invoker) {
    return getExtensions(invoker, EP_NAME);
  }

  @NotNull
  public Set<MetricEvent> getMetrics() {
    return getUsages().stream().
      filter(descriptor -> descriptor.getValue() > 0).
      map(descriptor -> {
      if (descriptor.getValue() == 1) {
        return MetricEventFactoryKt.newMetric(descriptor.getKey(), descriptor.getData());
      }
      return MetricEventFactoryKt.newCounterMetric(descriptor.getKey(), descriptor.getValue(), descriptor.getData());
    }).collect(Collectors.toSet());
  }

  /**
   * @deprecated use {@link ApplicationUsagesCollector#getMetrics()}
   */
  @NotNull
  @Deprecated
  public Set<UsageDescriptor> getUsages() {
    return Collections.emptySet();
  }

  @Nullable
  public FeatureUsageData getData() {
    return null;
  }
}