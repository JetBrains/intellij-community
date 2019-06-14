// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class VcsLogUsageTriggerCollector {

  public static void triggerUsage(@NotNull AnActionEvent e, @NotNull Object action) {
    triggerUsage(e, action, null);
  }

  public static void triggerUsage(@NotNull AnActionEvent e, @NotNull Object action, @Nullable Consumer<FeatureUsageData> configurator) {
    triggerUsage("action.called", data -> {
      addContext(data, e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI) != null);
      data.addInputEvent(e);
      data.addData("class", action.getClass().getName());
      if (configurator != null) configurator.accept(data);
    });
  }

  public static void triggerUsage(@NotNull String text, boolean isFromHistory, @Nullable Consumer<FeatureUsageData> configurator) {
    triggerUsage(text, data -> {
      addContext(data, isFromHistory);
      if (configurator != null) configurator.accept(data);
    });
  }

  public static void triggerUsage(@NotNull String text, @Nullable Consumer<FeatureUsageData> configurator) {
    FeatureUsageData data = new FeatureUsageData();
    if (configurator != null) configurator.accept(data);
    FUCounterUsageLogger.getInstance().logEvent("vcs.log.trigger", text, data);
  }

  private static void addContext(@NotNull FeatureUsageData data, boolean isFromHistory) {
    data.addData("context", isFromHistory ? "history" : "log");
  }
}
