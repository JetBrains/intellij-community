// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class FileTypeOpenUsageTriggerCollector {

  private static final FeatureUsageGroup GROUP_ID =  new FeatureUsageGroup("statistics.file.types.open",1);

  public static void trigger(@NotNull Project project, @NotNull FileType fileType) {
    if (StatisticsUtilKt.getPluginType(fileType.getClass()).isSafeToReport()) {
      FeatureUsageLogger.INSTANCE.log(GROUP_ID, fileType.getName(), StatisticsUtilKt.createData(project, null));
    }
  }
}
