// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.ide.actions.ActionsCollector;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionWithDelegate;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author Konstantin Bulenkov
 */
public class ActionsCollectorImpl extends ActionsCollector {
  private static final String GROUP = "actions";
  public static final String DEFAULT_ID = "third.party";

  private static final ActionsBuiltInWhitelist ourWhitelist = ActionsBuiltInWhitelist.getInstance();

  private final Map<AnAction, String> myOtherActions = ContainerUtil.createWeakMap();

  @Override
  public void record(@Nullable String actionId, @Nullable InputEvent event, @NotNull Class context) {
    String recorded = StringUtil.isNotEmpty(actionId) && ourWhitelist.isCustomAllowedAction(actionId) ? actionId : DEFAULT_ID;
    FeatureUsageData data = new FeatureUsageData().addData("action_id", recorded);
    if (event instanceof KeyEvent) {
      data.addInputEvent((KeyEvent)event);
    }
    else if (event instanceof MouseEvent) {
      data.addInputEvent((MouseEvent)event);
    }
    FUCounterUsageLogger.getInstance().logEvent(GROUP, "custom.action.invoked", data);
  }

  @Override
  public void record(@Nullable Project project, @Nullable AnAction action, @Nullable AnActionEvent event, @Nullable Language lang) {
    record(GROUP, "action.invoked", project, action, event, data -> {
      if (lang != null) data.addCurrentFile(lang);
    });
  }

  /**
   * @deprecated Reporting dynamic action id as event id is deprecated. All event ids should be enumerable and known before ahead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2019.3")
  public static void record(@NotNull String groupId,
                            @Nullable Project project,
                            @Nullable AnAction action,
                            @Nullable AnActionEvent event,
                            @Nullable Consumer<FeatureUsageData> configurator) {
    record(groupId, null, project, action, event, configurator);
  }

  public static void record(@NotNull String groupId,
                            @Nullable String eventId,
                            @Nullable Project project,
                            @Nullable AnAction action,
                            @Nullable AnActionEvent event,
                            @Nullable Consumer<FeatureUsageData> configurator) {
    if (action == null) return;

    PluginInfo info = PluginInfoDetectorKt.getPluginInfo(action.getClass());
    FeatureUsageData data = new FeatureUsageData().addProject(project).addPluginInfo(info);

    if (event != null) {
      data.addInputEvent(event).
        addPlace(event.getPlace()).
        addData("context_menu", event.isFromContextMenu());
    }

    if (configurator != null) {
      configurator.accept(data);
    }

    String actionClassName = info.isSafeToReport() ? action.getClass().getName() : DEFAULT_ID;
    String actionId = ((ActionsCollectorImpl)getInstance()).getActionId(info, action);
    if (action instanceof ActionWithDelegate) {
      Object delegate = ((ActionWithDelegate<?>)action).getDelegate();
      PluginInfo delegateInfo = PluginInfoDetectorKt.getPluginInfo(delegate.getClass());
      actionId = delegateInfo.isSafeToReport() ? delegate.getClass().getName() : DEFAULT_ID;
      data.addData("class", actionId);
      data.addData("parent", actionClassName);
    }
    else {
      data.addData("class", actionClassName);
    }
    data.addData("action_id", actionId);

    String reportedEventId = StringUtil.notNullize(eventId, actionId);
    FUCounterUsageLogger.getInstance().logEvent(groupId, reportedEventId, data);
  }

  @NotNull
  private String getActionId(@NotNull PluginInfo pluginInfo, @NotNull AnAction action) {
    if (!pluginInfo.isSafeToReport()) {
      return DEFAULT_ID;
    }
    String actionId = ActionManager.getInstance().getId(action);
    if (actionId == null && action instanceof ActionIdProvider) {
      actionId = ((ActionIdProvider)action).getId();
    }
    if (actionId != null && !canReportActionId(actionId)) {
      return action.getClass().getName();
    }
    if (actionId == null) {
      actionId = myOtherActions.get(action);
    }
    return actionId != null ? actionId : action.getClass().getName();
  }

  public static boolean canReportActionId(@NotNull String actionId) {
    return ourWhitelist.isWhitelistedActionId(actionId);
  }

  @Override
  public void onActionConfiguredByActionId(@NotNull AnAction action, @NotNull String actionId) {
    if (canReportActionId(actionId)) {
      myOtherActions.put(action, actionId);
    }
  }

  public static void onActionLoadedFromXml(@NotNull AnAction action, @NotNull String actionId, @Nullable IdeaPluginDescriptor plugin) {
    ourWhitelist.addActionLoadedFromXml(actionId, plugin);
  }

  public static void onActionsLoadedFromKeymapXml(@NotNull Keymap keymap, @NotNull Set<String> actionIds) {
    ourWhitelist.addActionsLoadedFromKeymapXml(keymap, actionIds);
  }
}
