// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.function.Supplier;

public class ProjectPathMacroManager extends PathMacroManager {
  private final Supplier<@SystemIndependent String> myBasePathPointer;
  private final @Nullable Supplier<@SystemIndependent String> myNamePointer;

  public ProjectPathMacroManager(@NotNull Project project) {
    super(PathMacros.getInstance());
    myBasePathPointer = project::getBasePath;
    myNamePointer = !project.isDefault() ? project::getName : null;
  }

  @NonInjectable
  private ProjectPathMacroManager(Supplier<@SystemIndependent String> basePathPointer,
                                  @Nullable Supplier<@SystemIndependent String> namePointer) {
    super(PathMacros.getInstance());
    myBasePathPointer = basePathPointer;
    myNamePointer = namePointer;
  }

  @NotNull
  @Override
  public ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = super.getExpandMacroMap();
    addFileHierarchyReplacements(result, PathMacroUtil.PROJECT_DIR_MACRO_NAME, myBasePathPointer.get());
    if (myNamePointer != null) {
      result.addMacroExpand(PathMacroUtil.PROJECT_NAME_MACRO_NAME, myNamePointer.get());
    }
    return result;
  }

  @NotNull
  @Override
  protected ReplacePathToMacroMap computeReplacePathMap() {
    ReplacePathToMacroMap result = super.computeReplacePathMap();
    addFileHierarchyReplacements(result, PathMacroUtil.PROJECT_DIR_MACRO_NAME, myBasePathPointer.get(), null);
    return result;
  }

  public static ProjectPathMacroManager createInstance(Supplier<@SystemIndependent String> basePathPointer,
                                                       @Nullable Supplier<@SystemIndependent String> namePointer) {
    return new ProjectPathMacroManager(basePathPointer, namePointer);
  }
}
