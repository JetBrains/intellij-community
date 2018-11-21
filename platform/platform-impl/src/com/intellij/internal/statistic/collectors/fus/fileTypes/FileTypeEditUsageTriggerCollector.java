// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.service.fus.collectors.FUSProjectUsageTrigger;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsageTriggerCollector;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class FileTypeEditUsageTriggerCollector extends ProjectUsageTriggerCollector {

  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.file.types.edit";
  }

  public static void trigger(@NotNull Project project, @NotNull FileType fileType) {
    if (StatisticsUtilKt.getPluginType(fileType.getClass()).isSafeToReport()) {
      FUSProjectUsageTrigger.getInstance(project).trigger(FileTypeEditUsageTriggerCollector.class, fileType.getName());
    }
  }
}
