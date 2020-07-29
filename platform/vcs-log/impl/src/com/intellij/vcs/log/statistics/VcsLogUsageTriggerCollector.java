// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

public final class VcsLogUsageTriggerCollector {

  public static void triggerUsage(@NotNull AnActionEvent e, @NotNull Object action) {
    triggerUsage(e, action, null);
  }

  public static void triggerUsage(@NotNull AnActionEvent e, @NotNull Object action, @Nullable Consumer<FeatureUsageData> configurator) {
    triggerUsage(VcsLogEvent.ACTION_CALLED, data -> {
      addContext(data, e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI) != null);
      data.addInputEvent(e);
      data.addData("class", action.getClass().getName());
      if (configurator != null) configurator.accept(data);
    });
  }

  public static void triggerUsage(@NotNull VcsLogEvent event, boolean isFromHistory, @Nullable Consumer<FeatureUsageData> configurator) {
    triggerUsage(event, data -> {
      addContext(data, isFromHistory);
      if (configurator != null) configurator.accept(data);
    });
  }

  public static void triggerUsage(@NotNull VcsLogEvent event, @Nullable Consumer<FeatureUsageData> configurator) {
    FeatureUsageData data = new FeatureUsageData();
    if (configurator != null) configurator.accept(data);
    FUCounterUsageLogger.getInstance().logEvent("vcs.log.trigger", event.getId(), data);
  }

  private static void addContext(@NotNull FeatureUsageData data, boolean isFromHistory) {
    data.addData("context", isFromHistory ? "history" : "log");
  }

  public static void triggerClick(@NonNls @NotNull String target) {
    triggerUsage(VcsLogEvent.TABLE_CLICKED, data -> data.addData("target", target));
  }

  public enum VcsLogEvent {
    ACTION_CALLED,
    FILTER_SET,
    TABLE_CLICKED,
    COLUMN_RESET,
    HISTORY_SHOWN,
    TAB_NAVIGATED;

    @NotNull
    String getId() {
      return name().toLowerCase(Locale.ENGLISH).replace('_', '.');
    }
  }
}
