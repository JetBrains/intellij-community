/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.components.impl;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

public class ProjectPathMacroManager extends BasePathMacroManager {
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
  public ReplacePathToMacroMap getReplacePathMap() {
    final ReplacePathToMacroMap result = super.getReplacePathMap();
    addFileHierarchyReplacements(result, PathMacroUtil.PROJECT_DIR_MACRO_NAME, myProject.getBasePath(), null);
    return result;
  }
}
