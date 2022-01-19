// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * <p>Use it to create a collector which records IDE state.</p>
 * <br/>
 * To implement a new collector:
 * <ol>
 *   <li>Inherit the class, implement {@link ApplicationUsagesCollector#getGroup()} and {@link ApplicationUsagesCollector#getMetrics()} and register collector in plugin.xml.
 *   See <i>fus-collectors.md</i> for more details.</li>
 *   <li>Implement custom validation rules if necessary. For more information see {@link IntellijSensitiveDataValidator}.</li>
 *   <li>If new group is implemented in a platform or a plugin built with IntelliJ Ultimate, YT issue will be created automatically</li>
 *   <li>Otherwise, create a YT issue in FUS project with group data scheme and descriptions to register it on the statistics metadata server</li>
 * </ol>
 * <br/>
 * To test collector:
 * <ol>
 *  <li>
 *    Open "Statistics Event Log" toolwindow.
 *  </li>
 *  <li>
 *    Add group to events test scheme with "Add Group to Events Test Scheme" action.<br/>
 *    {@link com.intellij.internal.statistic.actions.scheme.AddGroupToTestSchemeAction}
 *  </li>
 *  <li>
 *    Record all state collectors with "Record State Collectors to Event Log" action.<br/>
 *    {@link com.intellij.internal.statistic.actions.RecordStateStatisticsEventLogAction}
 *  </li>
 * </ol>
 * <br/>
 * For more information see <i>fus-collectors.md</i>
 *
 * @see ProjectUsagesCollector
 * @see CounterUsagesCollector
 */
@ApiStatus.Internal
public abstract class ApplicationUsagesCollector extends FeatureUsagesCollector {

  @ApiStatus.Internal
  public static final ExtensionPointName<ApplicationUsagesCollector> EP_NAME =
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
   *
   * @return <b>Not empty</b> set of metrics
   */
  @NotNull
  public abstract Set<MetricEvent> getMetrics();
}