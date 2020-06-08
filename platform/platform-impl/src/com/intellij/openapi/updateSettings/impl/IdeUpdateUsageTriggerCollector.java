// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IdeUpdateUsageTriggerCollector {

  public static void triggerUpdateDialog(@Nullable UpdateChain patches, boolean isRestartCapable) {
    final FeatureUsageData data = new FeatureUsageData();
    if (patches == null) {
      data.addData("patches", "not.available");
    }
    else if (!isRestartCapable) {
      data.addData("patches", "manual");
    }
    else {
      data.addData("patches", "auto");
    }
    FUCounterUsageLogger.getInstance().logEvent("ide.self.update", "dialog.shown", data);
  }

  public static void trigger(@NotNull String feature) {
    FUCounterUsageLogger.getInstance().logEvent("ide.self.update", feature);
  }
}
