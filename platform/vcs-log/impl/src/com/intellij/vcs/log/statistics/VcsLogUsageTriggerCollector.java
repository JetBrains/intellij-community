// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;

public class VcsLogUsageTriggerCollector {

  private static final FeatureUsageGroup GROUP = new FeatureUsageGroup("statistics.vcs.log.trigger",1);

  public static void triggerUsage(@NotNull AnActionEvent e) {
    String text = e.getPresentation().getText();
    if (text != null) {
      triggerUsage(e, text);
    }
  }

  public static void triggerUsage(@NotNull AnActionEvent e, @NotNull String text) {
    triggerUsage(text, e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI) != null);
  }

  public static void triggerUsage(@NotNull String text) {
    triggerUsage(text, false);
  }

  public static void triggerUsage(@NotNull String text, boolean isFromHistory) {
    String prefix = isFromHistory ? "history." : "log.";
    String feature = prefix + UsageDescriptorKeyValidator.ensureProperKey(text);
    FeatureUsageLogger.INSTANCE.log(GROUP, feature);
  }
}
