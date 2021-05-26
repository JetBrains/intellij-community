// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.ide.actions.ActionsCollector;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.eventLog.events.FusInputEvent;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class ActionsCollectorImpl extends ActionsCollector {
  public static final String DEFAULT_ID = "third.party";

  private static final ActionsBuiltInWhitelist ourWhitelist = ActionsBuiltInWhitelist.getInstance();

  @Override
  public void record(@Nullable String actionId, @Nullable InputEvent event, @NotNull Class context) {
    recordCustomActionInvoked(null, actionId, event, context);
  }

  public static void recordCustomActionInvoked(@Nullable Project project, @Nullable String actionId, @Nullable InputEvent event, @NotNull Class context) {
    String recorded = StringUtil.isNotEmpty(actionId) && ourWhitelist.isCustomAllowedAction(actionId) ? actionId : DEFAULT_ID;
    ActionsEventLogGroup.CUSTOM_ACTION_INVOKED.log(project, recorded, new FusInputEvent(event, null));
  }

  @Override
  public void record(@Nullable Project project, @Nullable AnAction action, @Nullable AnActionEvent event, @Nullable Language lang) {
    recordActionInvoked(project, action, event, Collections.singletonList(EventFields.CurrentFile.with(lang)));
  }

  public static void recordActionInvoked(@Nullable Project project,
                                         @Nullable AnAction action,
                                         @Nullable AnActionEvent event,
                                         @NotNull List<EventPair<?>> customData) {
    record(ActionsEventLogGroup.ACTION_INVOKED, project, action, event, customData);
  }

  public static void record(VarargEventId eventId,
                            @Nullable Project project,
                            @Nullable AnAction action,
                            @Nullable AnActionEvent event,
                            @Nullable List<EventPair<?>> customData) {
    if (action == null) return;
    PluginInfo info = PluginInfoDetectorKt.getPluginInfo(action.getClass());

    List<EventPair<?>> data = new ArrayList<>();
    data.add(EventFields.PluginInfoFromInstance.with(action));

    if (event != null) {
      if (action instanceof ToggleAction) {
        data.add(ActionsEventLogGroup.TOGGLE_ACTION.with(!((ToggleAction)action).isSelected(event)));
      }
      data.addAll(actionEventData(event));
    }
    if (project != null) {
      data.add(ActionsEventLogGroup.DUMB.with(DumbService.isDumb(project)));
    }
    if (customData != null) {
      data.addAll(customData);
    }
    addActionClass(data, action, info);
    eventId.log(project, data);
  }

  public static @NotNull List<@NotNull EventPair<?>> actionEventData(@NotNull AnActionEvent event) {
    List<EventPair<?>> data = new ArrayList<>();
    data.add(EventFields.InputEvent.with(FusInputEvent.from(event)));
    data.add(EventFields.ActionPlace.with(event.getPlace()));
    data.add(ActionsEventLogGroup.CONTEXT_MENU.with(event.isFromContextMenu()));
    return data;
  }

  @NotNull
  public static String addActionClass(@NotNull List<EventPair<?>> data,
                                      @NotNull AnAction action,
                                      @NotNull PluginInfo info) {
    String actionClassName = info.isSafeToReport() ? action.getClass().getName() : DEFAULT_ID;
    String actionId = getActionId(info, action);
    if (action instanceof ActionWithDelegate) {
      Object delegate = ((ActionWithDelegate<?>)action).getDelegate();
      PluginInfo delegateInfo = PluginInfoDetectorKt.getPluginInfo(delegate.getClass());
      if (delegate instanceof AnAction) {
        AnAction delegateAction = (AnAction)delegate;
        actionId = getActionId(delegateInfo, delegateAction);
      } else {
        actionId = delegateInfo.isSafeToReport() ? delegate.getClass().getName() : DEFAULT_ID;
      }
      data.add(ActionsEventLogGroup.ACTION_CLASS.with(actionId));
      data.add(ActionsEventLogGroup.ACTION_PARENT.with(actionClassName));
    }
    else {
      data.add(ActionsEventLogGroup.ACTION_CLASS.with(actionClassName));
    }
    data.add(ActionsEventLogGroup.ACTION_ID.with(actionId));
    return actionId;
  }

  public static void addActionClass(@NotNull FeatureUsageData data,
                                      @NotNull AnAction action,
                                      @NotNull PluginInfo info) {
    List<EventPair<?>> list = new ArrayList<>();
    addActionClass(list, action, info);
    for (EventPair<?> pair : list) {
      data.addData(pair.component1().getName(), pair.component2().toString());
    }
  }


  @NotNull
  private static String getActionId(@NotNull PluginInfo pluginInfo, @NotNull AnAction action) {
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
      actionId = ourWhitelist.getDynamicActionId(action);
    }
    return actionId != null ? actionId : action.getClass().getName();
  }

  public static boolean canReportActionId(@NotNull String actionId) {
    return ourWhitelist.isWhitelistedActionId(actionId);
  }

  @Override
  public void onActionConfiguredByActionId(@NotNull AnAction action, @NotNull String actionId) {
    ourWhitelist.registerDynamicActionId(action, actionId);
  }

  public static void onActionLoadedFromXml(@NotNull AnAction action, @NotNull String actionId, @Nullable IdeaPluginDescriptor plugin) {
    ourWhitelist.addActionLoadedFromXml(actionId, plugin);
  }

  public static void onActionsLoadedFromKeymapXml(@NotNull Keymap keymap, @NotNull Set<String> actionIds) {
    ourWhitelist.addActionsLoadedFromKeymapXml(keymap, actionIds);
  }
}
