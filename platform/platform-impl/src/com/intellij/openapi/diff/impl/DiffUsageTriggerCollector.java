// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl;

import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsageTriggerCollector;
import com.intellij.internal.statistic.service.fus.collectors.FUSApplicationUsageTrigger;
import org.jetbrains.annotations.NotNull;

public class DiffUsageTriggerCollector extends ApplicationUsageTriggerCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.vcs.diff.trigger";
  }

  public static void trigger(String feature) {
    FUSApplicationUsageTrigger.getInstance().trigger(DiffUsageTriggerCollector.class, feature);
  }
}
