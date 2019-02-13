// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.KeyedLazyInstanceEP;
import org.jetbrains.annotations.NotNull;

public class FileTypeUsageCounterCollector {
  private static final ExtensionPointName<KeyedLazyInstanceEP<FileTypeUsageSchemaDescriptor>> EP =
    ExtensionPointName.create("com.intellij.fileTypeUsageSchemaDescriptor");

  public static void triggerEdit(@NotNull Project project, @NotNull VirtualFile file) {
    trigger(project, file, "edit");
  }

  public static void triggerOpen(@NotNull Project project, @NotNull VirtualFile file) {
    trigger(project, file, "open");
  }

  private static void trigger(@NotNull Project project,
                              @NotNull VirtualFile file,
                              @NotNull String type) {
    final FeatureUsageData data = new FeatureUsageData().addData("type", type);
    FileType fileType = file.getFileType();

    for (KeyedLazyInstanceEP<FileTypeUsageSchemaDescriptor> ext : EP.getExtensionList()) {
      FileTypeUsageSchemaDescriptor instance = ext.getInstance();

      String schema = instance.describeSchema(file);
      if (schema != null) {
        if (!StatisticsUtilKt.getPluginType(instance.getClass()).isDevelopedByJetBrains()) {
          schema = "third.party";
        }
        data.addData("schema", schema);
        break;
      }
    }

    final String id = FileTypeUsagesCollector.toReportedId(fileType, data);
    FUCounterUsageLogger.getInstance().logEvent(project, "file.types.usage", id, data);
  }
}
