// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class FileExtensionUsagesCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    final Set<String> extensions = new HashSet<>();
    ProjectFileIndex.getInstance(project).iterateContent(fileOrDir -> {
      if (!fileOrDir.isDirectory()) {
        extensions.add(fileOrDir.getExtension() != null ? fileOrDir.getExtension() : fileOrDir.getName());
      }
      return true;
    });
    return ContainerUtil.map2Set(extensions, (NotNullFunction<String, UsageDescriptor>)extension -> new UsageDescriptor(extension, 1));
  }

  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.file.extensions";
  }
}
