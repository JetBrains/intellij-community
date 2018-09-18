// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

public class ProjectPathMacroManager extends PathMacroManager {
  private final Project myProject;

  public ProjectPathMacroManager(PathMacros pathMacros, Project project) {
    super(pathMacros);
    myProject = project;
  }

  @NotNull
  @Override
  public ExpandMacroToPathMap getExpandMacroMap() {
    final ExpandMacroToPathMap result = super.getExpandMacroMap();
    addFileHierarchyReplacements(result, PathMacroUtil.PROJECT_DIR_MACRO_NAME, myProject.getBasePath());
    return result;
  }

  @NotNull
  @Override
  protected ReplacePathToMacroMap computeReplacePathMap() {
    final ReplacePathToMacroMap result = super.computeReplacePathMap();
    addFileHierarchyReplacements(result, PathMacroUtil.PROJECT_DIR_MACRO_NAME, myProject.getBasePath(), null);
    return result;
  }
}
