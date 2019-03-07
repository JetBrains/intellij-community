// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class FileTypeUsageCounterCollector {

  public static void triggerEdit(@NotNull Project project, @NotNull FileType fileType) {
    trigger(project, fileType, "edit");
  }

  public static void triggerOpen(@NotNull Project project, @NotNull FileType fileType) {
    trigger(project, fileType, "open");
  }

  private static void trigger(@NotNull Project project,
                              @NotNull FileType fileType,
                              @NotNull String type) {
    final FeatureUsageData data = new FeatureUsageData().addData("type", type);
    final String id = FileTypeUsagesCollector.toReportedId(fileType, data);
    FUCounterUsageLogger.getInstance().logEvent(project, "file.types.usage", id, data);
  }
}
