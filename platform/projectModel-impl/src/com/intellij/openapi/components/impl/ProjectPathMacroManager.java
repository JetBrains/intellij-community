// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.project.ProjectStoreOwner;
import com.intellij.serviceContainer.NonInjectable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.Map;
import java.util.function.Supplier;

public class ProjectPathMacroManager extends PathMacroManager {
  private final @NotNull Supplier<@Nullable @SystemIndependent String> projectFilePathPointer;
  private final @NotNull Supplier<@Nullable @SystemIndependent String> basePathPointer;
  private final @Nullable Supplier<@NotNull @SystemIndependent String> namePointer;

  public ProjectPathMacroManager(@NotNull Project project) {
    super(PathMacros.getInstance());

    projectFilePathPointer = project::getProjectFilePath;
    basePathPointer = () -> {
      if (project instanceof ProjectStoreOwner projectStoreOwner) {
        return FileUtilRt.toSystemIndependentName(projectStoreOwner.getComponentStore().getStoreDescriptor().getHistoricalProjectBasePath().toString());
      }
      else {
        return project.getBasePath();
      }
    };
    namePointer = project.isDefault() ? null : project::getName;
  }

  @NonInjectable
  private ProjectPathMacroManager(@NotNull Supplier<@Nullable @SystemIndependent String> projectFilePathPointer,
                                  @NotNull Supplier<@Nullable @SystemIndependent String> basePathPointer,
                                  @Nullable Supplier<@NotNull @SystemIndependent String> namePointer) {
    super(PathMacros.getInstance());
    this.projectFilePathPointer = projectFilePathPointer;
    this.basePathPointer = basePathPointer;
    this.namePointer = namePointer;
  }

  @Override
  public @NotNull ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = super.getExpandMacroMap();
    addFileHierarchyReplacements(result, PathMacroUtil.PROJECT_DIR_MACRO_NAME, basePathPointer.get());
    if (namePointer != null) {
      result.addMacroExpand(PathMacroUtil.PROJECT_NAME_MACRO_NAME, namePointer.get());
    }
    String projectFile = projectFilePathPointer.get();
    if (projectFile != null) {
      for (Map.Entry<String, String> entry : ProjectWidePathMacroContributor.getAllMacros(projectFile).entrySet()) {
        result.addMacroExpand(entry.getKey(), entry.getValue());
      }
    }
    return result;
  }

  @Override
  protected @NotNull ReplacePathToMacroMap computeReplacePathMap() {
    ReplacePathToMacroMap result = super.computeReplacePathMap();
    addFileHierarchyReplacements(result, PathMacroUtil.PROJECT_DIR_MACRO_NAME, basePathPointer.get(), null);
    String projectFile = projectFilePathPointer.get();
    if (projectFile != null) {
      for (Map.Entry<String, String> entry : ProjectWidePathMacroContributor.getAllMacros(projectFile).entrySet()) {
        result.addMacroReplacement(entry.getValue(), entry.getKey());
      }
    }
    return result;
  }

  public static ProjectPathMacroManager createInstance(@NotNull Supplier<@Nullable @SystemIndependent String> projectFilePathPointer,
                                                       @NotNull Supplier<@SystemIndependent @Nullable String> basePathPointer,
                                                       @Nullable Supplier<@SystemIndependent @NotNull String> namePointer) {
    return new ProjectPathMacroManager(projectFilePathPointer, basePathPointer, namePointer);
  }
}
