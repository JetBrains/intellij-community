// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.ide.actions.ActionsCollector;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.collectors.fus.ui.persistence.ShortcutsCollector;
import com.intellij.internal.statistic.eventLog.FeatureUsageDataBuilder;
import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
@State(name = "ActionsCollector", storages = @Storage(
  value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED, deprecated = true)
)
public class ActionsCollectorImpl extends ActionsCollector implements PersistentStateComponent<ActionsCollector.State> {
  private static final FeatureUsageGroup GROUP = new FeatureUsageGroup("actions", 1);
  private static final String DEFAULT_ID = "third.party.plugin.action";

  private static final HashMap<String, String> ourPrefixesBlackList = new HashMap<>();

  static {
    ourPrefixesBlackList.put("RemoteTool_", "Remote External Tool");
    ourPrefixesBlackList.put("Tool_", "External Tool");
    ourPrefixesBlackList.put("Ant_", "Ant_");
    ourPrefixesBlackList.put("Maven_", "Maven_");
    ourPrefixesBlackList.put("ExternalSystem_", "ExternalSystem_");
    ourPrefixesBlackList.put("Macro.", "Invoke Macro");
  }

  @Override
  public void record(@Nullable String actionId, @NotNull Class context, @Nullable AnActionEvent event) {
    if (actionId == null) return;

    boolean isContextMenu = event != null && event.isFromContextMenu();
    final String place = event != null ? event.getPlace() : "";

    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(context);
    final FeatureUsageDataBuilder builder = new FeatureUsageDataBuilder().
      addFeatureContext(FUSUsageContext.OS_CONTEXT).
      addPluginInfo(info).
      addData("context_menu", isContextMenu);

    final boolean isDevelopedByJB = info.isDevelopedByJetBrains();
    if (isContextMenu && isDevelopedByJB) {
      builder.addPlace(place);
    }

    final String inputEvent = ShortcutsCollector.getInputEventText(event);
    if (StringUtil.isNotEmpty(inputEvent)) {
      builder.addData("input_event", inputEvent);
    }

    final String key = isDevelopedByJB ? toReportedId(actionId) : DEFAULT_ID;
    FeatureUsageLogger.INSTANCE.log(GROUP, key, builder.createData());
  }

  @NotNull
  private static String toReportedId(@NotNull String actionId) {
    final String key = ConvertUsagesUtil.escapeDescriptorName(actionId);
    for (Map.Entry<String, String> prefix : ourPrefixesBlackList.entrySet()) {
      if (key.startsWith(prefix.getKey())) {
        return prefix.getValue();
      }
    }
    return key;
  }

  private State myState = new State();

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
  }
}
