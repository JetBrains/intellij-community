// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsLogUsageTriggerCollector {

  public static void triggerUsage(@NotNull AnActionEvent e, @NotNull Object action) {
    String name = action.getClass().getName();
    if (name.contains(".")) name = name.substring(name.lastIndexOf(".") + 1);
    triggerUsage(e, name);
  }

  public static void triggerUsage(@NotNull AnActionEvent e, @NotNull String text) {
    triggerUsage(text, e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI) != null, e);
  }

  public static void triggerUsage(@NotNull String text) {
    triggerUsage(text, "", null);
  }

  public static void triggerUsage(@NotNull String text, boolean isFromHistory, @Nullable AnActionEvent e) {
    triggerUsage(text, isFromHistory ? "history" : "log", e);
  }

  private static void triggerUsage(@NotNull String text, @NotNull String context, @Nullable AnActionEvent event) {
    FeatureUsageData featureUsageData = new FeatureUsageData().addData("context", context);
    if (event != null) {
      featureUsageData.addInputEvent(event);
    }
    FUCounterUsageLogger.getInstance().logEvent("vcs.log.trigger", text, featureUsageData);
  }
}
