// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ProjectTypesProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.jetbrains.idea.devkit.util.PsiUtil.isPluginProject;

final class DevKitProjectTypeProvider implements ProjectTypesProvider {

  private static final String IDE_PLUGIN_PROJECT = "INTELLIJ_PLUGIN";

  @Override
  public @NotNull Collection<ProjectType> inferProjectTypes(@NotNull Project project) {
    if (isPluginProject(project)) {
      return singletonList(new ProjectType(IDE_PLUGIN_PROJECT));
    }
    return emptyList();
  }
}
