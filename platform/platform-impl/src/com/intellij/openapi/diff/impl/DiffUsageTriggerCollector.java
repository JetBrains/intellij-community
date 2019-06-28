// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl;

import com.intellij.diff.DiffTool;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiffUsageTriggerCollector {

  private static void trigger(@NotNull String eventId, @NotNull FeatureUsageData data) {
    FUCounterUsageLogger.getInstance().logEvent("vcs.diff.trigger", eventId, data);
  }

  public static void trigger(@NotNull String feature, @NotNull Enum value, @Nullable String place) {
    FeatureUsageData data = new FeatureUsageData()
      .addData("value", value.name())
      .addData("diff_place", StringUtil.notNullize(place, "unknown"));

    trigger(feature, data);
  }

  public static void trigger(@NotNull String feature, @NotNull DiffTool diffTool, @Nullable String place) {
    FeatureUsageData data = new FeatureUsageData()
      .addPluginInfo(PluginInfoDetectorKt.getPluginInfo(diffTool.getClass()))
      .addData("value", diffTool.getName())
      .addData("diff_place", StringUtil.notNullize(place, "unknown"));

    trigger(feature, data);
  }
}
