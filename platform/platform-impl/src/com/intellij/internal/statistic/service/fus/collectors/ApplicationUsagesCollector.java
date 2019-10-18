// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.beans.MetricEventFactoryKt;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>Use it to create a collector which records IDE state.</p>
 *
 * To implement a new collector:
 * <ol>
 *   <li>Inherit the class, implement {@link ApplicationUsagesCollector#getMetrics()} and register collector in XML;</li>
 *   <li>Specify collectors data scheme and implement custom validation rules if necessary.<br/>
 *   For more information see {@link SensitiveDataValidator};</li>
 *   <li>Create an <a href="https://youtrack.jetbrains.com/issues/FUS">issue</a> to add group, its data scheme and description to the whitelist;</li>
 * </ol>
 *
 * @see ProjectUsagesCollector
 * @see FUCounterUsageLogger
 */
public abstract class ApplicationUsagesCollector extends FeatureUsagesCollector {
  private static final ExtensionPointName<ApplicationUsagesCollector> EP_NAME =
    ExtensionPointName.create("com.intellij.statistics.applicationUsagesCollector");

  @NotNull
  public static Set<ApplicationUsagesCollector> getExtensions(@NotNull UsagesCollectorConsumer invoker) {
    return getExtensions(invoker, EP_NAME);
  }

  /**
   * Implement this method to calculate metrics.
   * <br/><br/>
   * {@link MetricEvent#eventId} should indicate what we measure, e.g. "configured.vcs", "module.jdk".<br/>
   * {@link MetricEvent#data} should contain the value of the measurement, e.g. {"name":"Git"}, {"version":"1.8", "vendor":"OpenJdk"}
   */
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