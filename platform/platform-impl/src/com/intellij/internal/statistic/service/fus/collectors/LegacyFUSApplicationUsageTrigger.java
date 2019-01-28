// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.components.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@State(name = "FUSApplicationUsageTrigger",
  storages = @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED, deprecated = true)
)
final public class LegacyFUSApplicationUsageTrigger implements PersistentStateComponent<LegacyFUSApplicationUsageTrigger.State> {
  private final State myState = new State();

  final static class State {
    @Transient
    List<String> sessions = ContainerUtil.newSmartList();
  }

  public static void cleanup() {
    ServiceManager.getService(LegacyFUSApplicationUsageTrigger.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull final State state) {
  }

}
