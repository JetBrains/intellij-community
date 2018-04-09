// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.project.ProjectKt;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Nikolay Matveev
 */
public class FileTypeUsagesCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.file.types";
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@NotNull final Project project) {
    return getDescriptors(project);
  }

  @NotNull
  public static Set<UsageDescriptor> getDescriptors(@NotNull Project project) {
    final Set<FileType> usedFileTypes = new HashSet<>();
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (fileTypeManager == null) {
      return Collections.emptySet();
    }
    final FileType[] registeredFileTypes = fileTypeManager.getRegisteredFileTypes();
    for (final FileType fileType : registeredFileTypes) {
      if (project.isDisposed()) {
        return Collections.emptySet();
      }
      ApplicationManager.getApplication().runReadAction(() -> {
        FileTypeIndex.processFiles(fileType, file -> {
          //skip files from .idea directory otherwise 99% of projects would have XML and PLAIN_TEXT file types
          if (!ProjectKt.getStateStore(project).isProjectFile(file)) {
            usedFileTypes.add(fileType);
            return false;
          }
          return true;
        }, GlobalSearchScope.projectScope(project));
      });
    }
    return ContainerUtil
      .map2Set(usedFileTypes, (NotNullFunction<FileType, UsageDescriptor>)fileType -> new UsageDescriptor(fileType.getName(), 1));
  }
}
