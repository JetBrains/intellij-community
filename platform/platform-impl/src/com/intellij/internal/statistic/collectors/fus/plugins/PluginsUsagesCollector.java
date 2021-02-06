// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.plugins;

import com.intellij.ide.plugins.*;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class PluginsUsagesCollector extends ApplicationUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("plugins",
                                                               4);
  private static final EventId1<PluginInfo> DISABLED_PLUGIN = GROUP.registerEvent("disabled.plugin",
                                                                                  EventFields.PluginInfo);
  private static final EventId1<PluginInfo> ENABLED_NOT_BUNDLED_PLUGIN = GROUP.registerEvent("enabled.not.bundled.plugin",
                                                                                             EventFields.PluginInfo);
  private static final EventId1<Integer> PER_PROJECT_ENABLED = GROUP.registerEvent("per.project.enabled",
                                                                                   EventFields.Count);
  private static final EventId1<Integer> PER_PROJECT_DISABLED = GROUP.registerEvent("per.project.disabled",
                                                                                    EventFields.Count);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  @NotNull
  public Set<MetricEvent> getMetrics() {
    Set<MetricEvent> result = new HashSet<>();
    result.addAll(getDisabledPlugins());
    result.addAll(getEnabledNonBundledPlugins());
    result.addAll(getPerProjectPlugins(PER_PROJECT_ENABLED,
                                       ProjectPluginTracker::getEnabledPluginsIds));
    result.addAll(getPerProjectPlugins(PER_PROJECT_DISABLED,
                                       ProjectPluginTracker::getDisabledPluginsIds));
    return result;
  }

  private static @NotNull Set<MetricEvent> getDisabledPlugins() {
    return DisabledPluginsState
      .disabledPlugins()
      .stream()
      .map(PluginInfoDetectorKt::getPluginInfoById)
      .map(DISABLED_PLUGIN::metric)
      .collect(Collectors.toUnmodifiableSet());
  }

  private static @NotNull Set<MetricEvent> getEnabledNonBundledPlugins() {
    return PluginManagerCore
      .getLoadedPlugins()
      .stream()
      .filter(IdeaPluginDescriptor::isEnabled)
      .filter(descriptor -> !descriptor.isBundled())
      .map(PluginInfoDetectorKt::getPluginInfoByDescriptor)
      .map(ENABLED_NOT_BUNDLED_PLUGIN::metric)
      .collect(Collectors.toUnmodifiableSet());
  }

  private static @NotNull Set<MetricEvent> getPerProjectPlugins(@NotNull EventId1<Integer> eventId,
                                                                @NotNull Function<@NotNull ProjectPluginTracker, @NotNull Set<PluginId>> countProducer) {
    return ProjectPluginTrackerManager
      .getInstance()
      .getTrackers()
      .values()
      .stream()
      .map(countProducer)
      .filter(set -> !set.isEmpty())
      .mapToInt(Set::size)
      .mapToObj(eventId::metric)
      .collect(Collectors.toUnmodifiableSet());
  }
}