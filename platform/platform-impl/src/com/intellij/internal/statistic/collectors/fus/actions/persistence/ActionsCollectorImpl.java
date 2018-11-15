// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.ide.actions.ActionsCollector;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.PluginId;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
  private static final String DEFAULT_ID = "third.party.plugin.action";
  private final Set<String> myJBActions = new THashSet<>();

  private static final HashMap<String, String> ourPrefixesBlackList = new HashMap<>();
  static {
    ourPrefixesBlackList.put("RemoteTool_", "Remote External Tool");
    ourPrefixesBlackList.put("Tool_", "External Tool");
    ourPrefixesBlackList.put("Macro.", "Invoke Macro");
  }

  @Override
  public void record(@Nullable String actionId, @NotNull Class context) {
    if (actionId == null) return;

    State state = getState();
    if (state == null) return;

    String key = toReportedId(actionId, context);
    FeatureUsageLogger.INSTANCE.log("actions", key);
    final Integer count = state.myValues.get(key);
    int value = count == null ? 1 : count + 1;
    state.myValues.put(key, value);
  }

  @NotNull
  private String toReportedId(@NotNull String actionId, @NotNull Class context) {
    String key = ConvertUsagesUtil.escapeDescriptorName(actionId);
    if (!myJBActions.contains(key)) {
      key = isDevelopedByJetBrains(context) ? key : DEFAULT_ID;
      myJBActions.add(key);
    }

    for (Map.Entry<String, String> prefix : ourPrefixesBlackList.entrySet()) {
      if (key.startsWith(prefix.getKey())) {
        return prefix.getValue();
      }
    }
    return key;
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
