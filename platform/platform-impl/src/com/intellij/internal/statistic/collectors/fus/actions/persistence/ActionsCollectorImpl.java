// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.ide.actions.ActionsCollector;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionWithDelegate;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.impl.BundledKeymapProvider;
import com.intellij.openapi.keymap.impl.DefaultKeymap;
import com.intellij.openapi.keymap.impl.DefaultKeymapImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author Konstantin Bulenkov
 */
public class ActionsCollectorImpl extends ActionsCollector {
  private static final String GROUP = "actions";
  public static final String DEFAULT_ID = "third.party";

  private final Set<String> myXmlActionIds = new HashSet<>();
  private final Map<AnAction, String> myOtherActions = ContainerUtil.createWeakMap();

  private static final Set<String> ourCustomActionWhitelist = ContainerUtil.newHashSet(
    "regexp.help", "ShowUsagesPopup.showSettings",
    "Reload Classes", "Progress Paused", "Progress Resumed", "DialogCancelAction", "DialogOkAction", "DoubleShortcut"
  );

  private boolean myKeymapsInitialized;

  public static boolean isCustomAllowedAction(@NotNull String actionId) {
    return DEFAULT_ID.equals(actionId) || ourCustomActionWhitelist.contains(actionId);
  }

  @Override
  public void record(@Nullable String actionId, @Nullable InputEvent event, @NotNull Class context) {
    String recorded = StringUtil.isNotEmpty(actionId) && ourCustomActionWhitelist.contains(actionId) ? actionId : DEFAULT_ID;
    FeatureUsageData data = new FeatureUsageData();
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
    FUCounterUsageLogger.getInstance().logEvent(groupId, actionId, data);
  }

  @NotNull
  private String getActionId(@NotNull PluginInfo pluginInfo, @NotNull AnAction action) {
    if (!pluginInfo.isSafeToReport()) {
      return DEFAULT_ID;
    }
    String actionId = ActionManager.getInstance().getId(action);
    if (actionId != null && !canReportActionId(actionId)) {
      return action.getClass().getName();
    }
    if (actionId == null) {
      actionId = myOtherActions.get(action);
    }
    return actionId != null ? actionId : action.getClass().getName();
  }

  private boolean canReportActionId(@NotNull String actionId) {
    ensureMapInitialized();
    return myXmlActionIds.contains(actionId);
  }

  private synchronized void ensureMapInitialized() {
    if (!myKeymapsInitialized) {
      for (Keymap keymap : DefaultKeymap.getInstance().getKeymaps()) {
        if (!(keymap instanceof DefaultKeymapImpl)) continue;
        Class<BundledKeymapProvider> providerClass = ((DefaultKeymapImpl)keymap).getProviderClass();
        if (!PluginInfoDetectorKt.getPluginInfo(providerClass).isDevelopedByJetBrains()) continue;
        myXmlActionIds.addAll(keymap.getActionIdList());
      }
      myKeymapsInitialized = true;
    }
  }

  @Override
  public void onActionConfiguredByActionId(@NotNull AnAction action, @NotNull String actionId) {
    if (canReportActionId(actionId)) {
      myOtherActions.put(action, actionId);
    }
  }

  /** @noinspection unused*/
  public void onActionLoadedFromXml(@NotNull AnAction action, @NotNull String actionId, @Nullable PluginId pluginId) {
    PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfoById(pluginId);
    if (pluginInfo.isSafeToReport()) {
      myXmlActionIds.add(actionId);
    }
  }

}
