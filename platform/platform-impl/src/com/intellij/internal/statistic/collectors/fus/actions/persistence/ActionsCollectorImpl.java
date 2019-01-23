// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.ide.actions.ActionsCollector;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.collectors.fus.ui.persistence.ShortcutsCollector;
import com.intellij.internal.statistic.eventLog.FeatureUsageDataBuilder;
import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.service.fus.collectors.FUSCounterUsageLogger;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.actionSystem.ActionManager;
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

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Set;

import static com.intellij.openapi.keymap.KeymapUtil.getKeystrokeText;

/**
 * @author Konstantin Bulenkov
 */
@State(name = "ActionsCollector", storages = @Storage(
  value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED, deprecated = true)
)
public class ActionsCollectorImpl extends ActionsCollector implements PersistentStateComponent<ActionsCollector.State> {
  private static final FeatureUsageGroup GROUP = new FeatureUsageGroup("actions", 2);
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
      final KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent((KeyEvent)event);
      if (keyStroke != null) {
        data.addData("input_event", getKeystrokeText(keyStroke));
      }
    }
    FUSCounterUsageLogger.logEvent(GROUP, recorded, data);
  }

  @Override
  public void record(@Nullable AnAction action, @Nullable AnActionEvent event) {
    if (action == null) return;

    boolean isContextMenu = event != null && event.isFromContextMenu();
    final String place = event != null ? event.getPlace() : "";

    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(action.getClass());
    final FeatureUsageDataBuilder data = new FeatureUsageDataBuilder().
      addFeatureContext(FUSUsageContext.OS_CONTEXT).
      addPluginInfo(info).
      addData("context_menu", isContextMenu);

    final boolean isDevelopedByJB = info.isDevelopedByJetBrains();
    if (isContextMenu && isDevelopedByJB) {
      data.addPlace(place);
    }

    final String inputEvent = ShortcutsCollector.getInputEventText(event);
    if (StringUtil.isNotEmpty(inputEvent)) {
      data.addData("input_event", inputEvent);
    }

    final String key = isDevelopedByJB ? toReportedId(action) : DEFAULT_ID;
    FUSCounterUsageLogger.logEvent(GROUP, key, data);
  }

  @NotNull
  private static String toReportedId(@NotNull AnAction action) {
    final String actionId = action.isGlobal() ? ActionManager.getInstance().getId(action) : null;
    if (StringUtil.isEmpty(actionId)) {
      return action.getClass().getName();
    }
    return ConvertUsagesUtil.escapeDescriptorName(actionId);
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
