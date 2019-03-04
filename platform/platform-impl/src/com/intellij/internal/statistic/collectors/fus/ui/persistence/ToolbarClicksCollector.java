// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui.persistence;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl.toReportedId;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "ToolbarClicksCollector",
  storages = {
    @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED, deprecated = true),
    @Storage(value = "statistics.toolbar.clicks.xml", roamingType = RoamingType.DISABLED, deprecated = true)
  }
)
public class ToolbarClicksCollector implements PersistentStateComponent<ToolbarClicksCollector.ClicksState> {

  public final static class ClicksState {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "action", valueAttributeName = "count")
    public Map<String, Integer> myValues = new HashMap<>();
  }

  private final ClicksState myState = new ClicksState();

  @Override
  public ClicksState getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull final ClicksState state) {
  }

  public static void record(@NotNull AnAction action, String place) {
    ToolbarClicksCollector collector = getInstance();
    if (collector != null) {
      final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(action.getClass());
      final FeatureUsageData data = new FeatureUsageData().addPluginInfo(info).addOS().addPlace(place);
      FUCounterUsageLogger.getInstance().logEvent("toolbar", toReportedId(info, action, data), data);
    }
  }

  public static ToolbarClicksCollector getInstance() {
    return ServiceManager.getService(ToolbarClicksCollector.class);
  }
}