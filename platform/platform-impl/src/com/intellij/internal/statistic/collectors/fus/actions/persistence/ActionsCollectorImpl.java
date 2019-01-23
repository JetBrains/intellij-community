// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.ide.actions.ActionsCollector;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.eventLog.FeatureUsageDataBuilder;
import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.service.fus.collectors.FUSCounterUsageLogger;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionWithDelegate;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
@State(name = "ActionsCollector", storages = @Storage(
  value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED, deprecated = true)
)
public class ActionsCollectorImpl extends ActionsCollector implements PersistentStateComponent<ActionsCollector.State> {
  private static final FeatureUsageGroup GROUP = new FeatureUsageGroup("actions", 3);
  private static final String DEFAULT_ID = "third.party";

  private static final Set<String> ourCustomActionWhitelist = ContainerUtil.newHashSet(
    "tooltip.actions.execute", "tooltip.actions.show.all", "tooltip.actions.show.description.gear",
    "tooltip.actions.show.description.shortcut", "tooltip.actions.show.description.morelink",
    "Ics.action.MergeSettings.text", "Ics.action.MergeSettings.text", "Ics.action.ResetToMySettings.text",
    "Reload Classes", "Progress Paused", "Progress Resumed", "DialogCancelAction", "DialogOkAction", "DoubleShortcut"
  );

  @Override
  public void record(@Nullable String actionId, @Nullable InputEvent event, @NotNull Class context) {
    final String recorded = StringUtil.isNotEmpty(actionId) && ourCustomActionWhitelist.contains(actionId) ? actionId : DEFAULT_ID;
    final FeatureUsageDataBuilder data = new FeatureUsageDataBuilder().addFeatureContext(FUSUsageContext.OS_CONTEXT);
    if (event instanceof KeyEvent) {
      data.addInputEvent((KeyEvent)event);
    }
    FUSCounterUsageLogger.logEvent(GROUP, recorded, data);
  }

  @Override
  public void record(@Nullable AnAction action, @Nullable AnActionEvent event) {
    if (action == null) return;

    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(action.getClass());
    final FeatureUsageDataBuilder data = new FeatureUsageDataBuilder().
      addFeatureContext(FUSUsageContext.OS_CONTEXT).
      addPluginInfo(info);

    if (event != null) {
      data.addInputEvent(event).
        addPlace(event.getPlace()).
        addData("context_menu", event.isFromContextMenu());
    }

    FUSCounterUsageLogger.logEvent(GROUP, toReportedId(info, action), data);
  }

  @NotNull
  public static String toReportedId(@NotNull PluginInfo info, @NotNull AnAction action) {
    String actionId = getActionId(info, action);
    if (actionId != null) {
      return actionId;
    }

    if (action instanceof ActionWithDelegate) {
      final Object delegate = ((ActionWithDelegate)action).getDelegate();
      final PluginInfo delegateInfo = PluginInfoDetectorKt.getPluginInfo(delegate.getClass());
      actionId = delegateInfo.isDevelopedByJetBrains() ? delegate.getClass().getName() : DEFAULT_ID;
    }
    else {
      actionId = action.getClass().getName();
    }
    return ConvertUsagesUtil.escapeDescriptorName(actionId);
  }

  @Nullable
  private static String getActionId(@NotNull PluginInfo info, @NotNull AnAction action) {
    if (!info.isDevelopedByJetBrains()) {
      return DEFAULT_ID;
    }

    return action.isGlobal() ? ActionManager.getInstance().getId(action) : null;
  }

  private final State myState = new State();

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
  }
}
