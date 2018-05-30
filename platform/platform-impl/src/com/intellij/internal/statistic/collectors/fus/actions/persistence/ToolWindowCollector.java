// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "ToolWindowCollector",
  storages = {
    @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED)
  }
)
public class ToolWindowCollector implements PersistentStateComponent<ToolWindowCollector.State> {
  public static ToolWindowCollector getInstance() {
    return ServiceManager.getService(ToolWindowCollector.class);
  }

  public void recordActivation(String toolWindowId) {
    record(toolWindowId + " by Activation");
  }

  //todo[kb] provide a proper way to track activations by clicks
  public void recordClick(String toolWindowId) {
    record(toolWindowId + " by Click");
  }

  private void record(String toolWindowId) {
    if (toolWindowId == null) return;
    State state = getState();
    if (state == null) return;

    String key = ConvertUsagesUtil.escapeDescriptorName(toolWindowId);
    FeatureUsageLogger.INSTANCE.log("toolwindow", key);
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

  public final static class State {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "toolWindow", valueAttributeName = "count")
    public Map<String, Integer> myValues = new HashMap<>();
  }
}
