// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.project.ProjectKt;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FileTypeUsagesCollector extends ProjectUsagesCollector {
  private static final String DEFAULT_ID = "third.party";

  @NotNull
  @Override
  public String getGroupId() {
    return "file.types";
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@NotNull final Project project) {
    return getDescriptors(project);
  }

  @NotNull
  public static Set<UsageDescriptor> getDescriptors(@NotNull Project project) {
    final Set<UsageDescriptor> descriptors = new HashSet<>();
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (fileTypeManager == null) {
      return Collections.emptySet();
    }
    final FileType[] registeredFileTypes = fileTypeManager.getRegisteredFileTypes();
    for (final FileType fileType : registeredFileTypes) {
      if (project.isDisposed()) {
        return Collections.emptySet();
      }

      final FeatureUsageData data = new FeatureUsageData();
      final String id = toReportedId(fileType, data);
      ApplicationManager.getApplication().runReadAction(() -> {
        FileTypeIndex.processFiles(fileType, file -> {
          //skip files from .idea directory otherwise 99% of projects would have XML and PLAIN_TEXT file types
          if (!ProjectKt.getStateStore(project).isProjectFile(file)) {
            descriptors.add(new UsageDescriptor(id, 1, data));
            return false;
          }
          return true;
        }, GlobalSearchScope.projectScope(project));
      });
    }
    return descriptors;
  }

  @NotNull
  public static String toReportedId(@NotNull FileType type, @NotNull FeatureUsageData data) {
    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(type.getClass());
    data.addPluginInfo(info);
    return info.isDevelopedByJetBrains() ? type.getName() : DEFAULT_ID;
  }
}
