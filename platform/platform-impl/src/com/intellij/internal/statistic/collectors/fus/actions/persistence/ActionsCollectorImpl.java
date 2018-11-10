// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.ide.actions.ActionsCollector;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
@State(name = "ActionsCollector", storages = @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED))
public class ActionsCollectorImpl extends ActionsCollector implements PersistentStateComponent<ActionsCollector.State> {
  private static final String DEFAULT_ID = "third.party.plugin.action";

  @Override
  public void record(@Nullable String actionId, @NotNull Class context, boolean isContextMenu, @Nullable String place) {
    if (actionId == null) return;

    State state = getState();
    if (state == null) return;

    String key = ConvertUsagesUtil.escapeDescriptorName(actionId);
    key = isDevelopedByJetBrains(context) ? key : DEFAULT_ID;

    final Map<String, Object> data = ContainerUtil.newHashMap(FUSUsageContext.OS_CONTEXT.getData());
    data.put("context_menu", isContextMenu);
    if (isContextMenu && place!= null) {
      data.put("place", place);
    }
    FeatureUsageLogger.INSTANCE.log("actions", key, data);

    Integer count = state.myValues.get(key);
    int value = count == null ? 1 : count + 1;
    state.myValues.put(key, value);
    if (isContextMenu) {
      if (place != null) {
        key = "[" + place + "] " + key;
      }
      count = state.myContextMenuValues.get(key);
      value = count == null ? 1 : count + 1;
      state.myContextMenuValues.put(key, value);
    }
  }

  private static boolean isDevelopedByJetBrains(@NotNull Class aClass) {
    final PluginId pluginId = PluginManagerCore.getPluginByClassName(aClass.getName());
    return StatisticsUtilKt.isDevelopedByJetBrains(pluginId);
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
