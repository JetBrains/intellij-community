// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class ProjectRootTestSourcesFilter extends TestSourcesFilter {
  @Override
  public boolean isTestSource(@NotNull VirtualFile file, @NotNull Project project) {
    return ReadAction.compute(() -> ProjectFileIndex.getInstance(project).isInTestSourceContent(file));
  }
}
