// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.ide.actions.ActionsCollector;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "ActionsCollector",
  storages = {
    @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED),
    @Storage(value = "statistics.actions.xml", roamingType = RoamingType.DISABLED, deprecated = true)
  }
)
public class ActionsCollectorImpl extends ActionsCollector implements PersistentStateComponent<ActionsCollector.State> {
  public void record(String actionId) {
    if (actionId == null) return;

    State state = getState();
    if (state == null) return;

    String key = ConvertUsagesUtil.escapeDescriptorName(actionId);
    FeatureUsageLogger.INSTANCE.log("actions", key);
    final Integer count = state.myValues.get(key);
    int value = count == null ? 1 : count + 1;
    state.myValues.put(key, value);
  }

  private State myState = new State();

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }
}
