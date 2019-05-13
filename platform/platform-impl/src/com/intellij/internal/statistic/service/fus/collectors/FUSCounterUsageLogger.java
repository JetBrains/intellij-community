// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.eventLog.FeatureUsageDataBuilder;
import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class FUSCounterUsageLogger {

  /**
   * Records new event in project dependent counter with group (e.g. 'dialogs', 'intentions')
   */
  public static void logEvent(@NotNull Project project,
                              @NotNull FeatureUsageGroup group,
                              @NotNull String event) {
    final Map<String, Object> data = new FeatureUsageDataBuilder().addProject(project).createData();
    FeatureUsageLogger.INSTANCE.log(group, event, data);
  }

  /**
   * Records new event in project dependent counter with group (e.g. 'dialogs', 'intentions').
   * Adds context information to the event, e.g. source and shortcut for an action.
   */
  public static void logEvent(@NotNull Project project,
                              @NotNull FeatureUsageGroup group,
                              @NotNull String event,
                              @NotNull FeatureUsageDataBuilder data) {
    FeatureUsageLogger.INSTANCE.log(group, event, data.addProject(project).createData());
  }

  /**
   * Records new event in application counter with group (e.g. 'dialogs', 'intentions')
   */
  public static void logEvent(@NotNull FeatureUsageGroup group,
                              @NotNull String event) {
    FeatureUsageLogger.INSTANCE.log(group, event);
  }

  /**
   * Records new event in application counter with group (e.g. 'dialogs', 'intentions').
   * Adds context information to the event, e.g. source and shortcut for an action.
   */
  public static void logEvent(@NotNull FeatureUsageGroup group,
                              @NotNull String event,
                              @NotNull FeatureUsageDataBuilder data) {
    FeatureUsageLogger.INSTANCE.log(group, event, data.createData());
  }
}
