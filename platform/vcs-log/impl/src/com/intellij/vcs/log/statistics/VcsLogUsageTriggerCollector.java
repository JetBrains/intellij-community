// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsageTriggerCollector;
import com.intellij.internal.statistic.service.fus.collectors.FUSApplicationUsageTrigger;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;

public class VcsLogUsageTriggerCollector extends ApplicationUsageTriggerCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.vcs.log.trigger";
  }

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
    FUSApplicationUsageTrigger.getInstance().trigger(VcsLogUsageTriggerCollector.class, feature);
  }
}
