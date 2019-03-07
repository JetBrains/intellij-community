// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl;

import com.intellij.diff.DiffTool;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import org.jetbrains.annotations.NotNull;

public class DiffUsageTriggerCollector {

  private static void trigger(@NotNull String feature) {
    FUCounterUsageLogger.getInstance().logEvent("vcs.diff.trigger", feature);
  }

  public static void trigger(@NotNull String feature, @NotNull Enum value) {
    trigger(feature + "." + value.name());
  }

  public static void trigger(@NotNull String feature, @NotNull DiffTool diffTool) {
    trigger(feature + "." + getDiffToolName(diffTool));
  }

  @NotNull
  private static String getDiffToolName(@NotNull DiffTool diffTool) {
    PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfo(diffTool.getClass());
    if (pluginInfo.isDevelopedByJetBrains()) return diffTool.getName();
    if (pluginInfo.isSafeToReport()) return "third.party." + pluginInfo.getId();
    return "third.party.other";
  }
}
