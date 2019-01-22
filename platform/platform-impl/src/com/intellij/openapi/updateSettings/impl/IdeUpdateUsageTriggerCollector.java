// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import org.jetbrains.annotations.NotNull;

public class IdeUpdateUsageTriggerCollector  {

  private static final FeatureUsageGroup GROUP = new FeatureUsageGroup("statistics.ide.self.update",1);

  public static void trigger(@NotNull String feature) {
    FeatureUsageLogger.INSTANCE.log(GROUP, feature);
  }
}
