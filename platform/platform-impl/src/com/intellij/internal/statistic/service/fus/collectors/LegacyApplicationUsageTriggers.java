// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.components.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@State(name = "StatisticsApplicationUsages", storages = @Storage(
  value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED, deprecated = true)
)
public class LegacyApplicationUsageTriggers implements PersistentStateComponent<LegacyApplicationUsageTriggers.State> {
  State myState = new State();

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
  }

  public static void cleanup() {
    ServiceManager.getService(LegacyApplicationUsageTriggers.class);
    ServiceManager.getService(LegacyUsageTrigger.class);
  }

  public final static class State {
    @Property(surroundWithTag = false)
    @XCollection
    List<String> groups = ContainerUtil.newSmartList();
  }

  public final static class CounterState {
    @Property(surroundWithTag = false)
    @XCollection
    Map<String, Integer> counts = ContainerUtil.newHashMap();
  }

  @com.intellij.openapi.components.State(name = "UsageTrigger", storages = @Storage(
    value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED, deprecated = true)
  )
  private static class LegacyUsageTrigger implements PersistentStateComponent<LegacyApplicationUsageTriggers.CounterState> {
    CounterState myState = new CounterState();

    @Nullable
    @Override
    public CounterState getState() {
      return myState;
    }

    @Override
    public void loadState(@NotNull CounterState state) {
    }
  }
}
