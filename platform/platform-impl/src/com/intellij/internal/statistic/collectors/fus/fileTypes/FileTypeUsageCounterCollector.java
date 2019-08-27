// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileTypeUsageCounterCollector {
  private static final Logger LOG = Logger.getInstance("#" + FileTypeUsageCounterCollector.class.getPackage().getName());

  private static final ExtensionPointName<FileTypeUsageSchemaDescriptorEP<FileTypeUsageSchemaDescriptor>> EP =
    ExtensionPointName.create("com.intellij.fileTypeUsageSchemaDescriptor");

  public static void triggerEdit(@NotNull Project project, @NotNull VirtualFile file) {
    trigger(project, file, "edit");
  }

  public static void triggerOpen(@NotNull Project project, @NotNull VirtualFile file) {
    trigger(project, file, "open");
  }

  private static void trigger(@NotNull Project project,
                              @NotNull VirtualFile file,
                              @NotNull String event) {
    final FeatureUsageData data = FileTypeUsagesCollector.newFeatureUsageData(file.getFileType());
    for (FileTypeUsageSchemaDescriptorEP<FileTypeUsageSchemaDescriptor> ext : EP.getExtensionList()) {
      FileTypeUsageSchemaDescriptor instance = ext.getInstance();
      if (ext.schema == null) {
        LOG.warn("Extension " + ext.implementationClass + " should define a 'schema' attribute");
        continue;
      }

      if (instance.describes(file)) {
        data.addData("schema", StatisticsUtilKt.getPluginType(instance.getClass()).isSafeToReport() ? ext.schema : "third.party");
        break;
      }
    }

    FUCounterUsageLogger.getInstance().logEvent(project, "file.types.usage", event, data);
  }

  public static final class FileTypeUsageSchemaDescriptorEP<T> extends BaseKeyedLazyInstance<T> implements KeyedLazyInstance<T> {
    // these must be public for scrambling compatibility
    @Attribute("schema")
    public String schema;

    @Attribute("implementationClass")
    public String implementationClass;

    @Nullable
    @Override
    protected String getImplementationClassName() {
      return implementationClass;
    }

    @Override
    public String getKey() {
      return schema;
    }
  }
}
