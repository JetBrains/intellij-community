// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui.persistence;

import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionWithDelegate;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "ToolbarClicksCollector",
  storages = {
    @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED),
    @Storage(value = "statistics.toolbar.clicks.xml", roamingType = RoamingType.DISABLED, deprecated = true)
  }
)
public class ToolbarClicksCollector implements PersistentStateComponent<ToolbarClicksCollector.ClicksState> {
  public final static class ClicksState {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "action", valueAttributeName = "count")
    public Map<String, Integer> myValues = new HashMap<>();
  }

  private ClicksState myState = new ClicksState();

  public ClicksState getState() {
    return myState;
  }

  public void loadState(@NotNull final ClicksState state) {
    myState = state;
  }

  public static void record(@NotNull AnAction action, String place) {
    String id = ActionManager.getInstance().getId(action);
    if (id == null) {
      if (action instanceof ActionWithDelegate) {
        id = ((ActionWithDelegate)action).getPresentableName();
      } else {
        id = action.getClass().getName();
      }
    }
    record(id, place);
  }

  public static void record(String actionId, String place) {
    ToolbarClicksCollector collector = getInstance();
    if (collector != null) {
      String key = ConvertUsagesUtil.escapeDescriptorName(actionId + "@" + place);
      ClicksState state = collector.getState();
      if (state != null) {
        final Integer count = state.myValues.get(key);
        int value = count == null ? 1 : count + 1;
        state.myValues.put(key, value);
      }
    }
  }

  public static ToolbarClicksCollector getInstance() {
    return ServiceManager.getService(ToolbarClicksCollector.class);
  }
}