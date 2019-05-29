// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.ide.actions.ActionsCollector;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionWithDelegate;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author Konstantin Bulenkov
 */
@State(name = "ActionsCollector", storages = @Storage(
  value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED, deprecated = true)
)
public class ActionsCollectorImpl extends ActionsCollector implements PersistentStateComponent<ActionsCollector.State> {
  private static final String GROUP = "actions";
  public static final String DEFAULT_ID = "third.party";

  private static final Set<String> ourCustomActionWhitelist = ContainerUtil.newHashSet(
    "tooltip.actions.execute", "tooltip.actions.show.all", "tooltip.actions.show.description.gear",
    "tooltip.actions.show.description.shortcut", "tooltip.actions.show.description.morelink",
    "Ics.action.MergeSettings.text", "Ics.action.MergeSettings.text", "Ics.action.ResetToMySettings.text",
    "Reload Classes", "Progress Paused", "Progress Resumed", "DialogCancelAction", "DialogOkAction", "DoubleShortcut"
  );

  public static boolean isCustomAllowedAction(@NotNull String actionId) {
    return DEFAULT_ID.equals(actionId) || ourCustomActionWhitelist.contains(actionId);
  }

  @Override
  public void record(@Nullable String actionId, @Nullable InputEvent event, @NotNull Class context) {
    final String recorded = StringUtil.isNotEmpty(actionId) && ourCustomActionWhitelist.contains(actionId) ? actionId : DEFAULT_ID;
    final FeatureUsageData data = new FeatureUsageData().addOS();
    if (event instanceof KeyEvent) {
      data.addInputEvent((KeyEvent)event);
    }
    else if (event instanceof MouseEvent) {
      data.addInputEvent((MouseEvent)event);
    }
    FUCounterUsageLogger.getInstance().logEvent(GROUP, recorded, data);
  }

  @Override
  public void record(@Nullable Project project, @Nullable AnAction action, @Nullable AnActionEvent event, @Nullable Language lang) {
    record(GROUP, project, action, event, data -> {
      if (lang != null) data.addCurrentFile(lang);
    });
  }

  public static void record(@NotNull String groupId,
                            @Nullable Project project,
                            @Nullable AnAction action,
                            @Nullable AnActionEvent event,
                            @Nullable Consumer<FeatureUsageData> configurator) {
    if (action == null) return;

    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(action.getClass());
    final FeatureUsageData data = new FeatureUsageData().addOS().addProject(project).addPluginInfo(info);

    if (event != null) {
      data.addInputEvent(event).
        addPlace(event.getPlace()).
        addData("context_menu", event.isFromContextMenu());
    }

    if (configurator != null) {
      configurator.accept(data);
    }

    FUCounterUsageLogger.getInstance().logEvent(groupId, toReportedId(info, action, data), data);
  }

  @NotNull
  public static String toReportedId(@NotNull PluginInfo info,
                                    @NotNull AnAction action,
                                    @NotNull FeatureUsageData data) {
    if (action instanceof ActionWithDelegate) {
      final String parent = getActionId(info, action, true);
      data.addData("parent", parent);

      final Object delegate = ((ActionWithDelegate)action).getDelegate();
      final PluginInfo delegateInfo = PluginInfoDetectorKt.getPluginInfo(delegate.getClass());
      return delegateInfo.isDevelopedByJetBrains() ? delegate.getClass().getName() : DEFAULT_ID;
    }
    return getActionId(info, action, false);
  }

  @NotNull
  private static String getActionId(@NotNull PluginInfo info, @NotNull AnAction action, boolean simpleName) {
    if (!info.isDevelopedByJetBrains()) {
      return DEFAULT_ID;
    }

    final String actionId = action.isGlobal() ? ActionManager.getInstance().getId(action) : null;
    if (actionId != null) {
      return ConvertUsagesUtil.escapeDescriptorName(actionId);
    }
    return simpleName ? action.getClass().getSimpleName() : action.getClass().getName();
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
