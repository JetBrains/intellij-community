/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

public class ModulePathMacroManager extends BasePathMacroManager {
  private final Module myModule;

  public ModulePathMacroManager(PathMacros pathMacros, Module module) {
    super(pathMacros);
    myModule = module;
  }

  @NotNull
  @Override
  public ExpandMacroToPathMap getExpandMacroMap() {
    final ExpandMacroToPathMap result = new ExpandMacroToPathMap();
    if (!myModule.isDisposed()) {
      addFileHierarchyReplacements(result, PathMacroUtil.MODULE_DIR_MACRO_NAME, PathMacroUtil.getModuleDir(myModule.getModuleFilePath()));
    }

    result.putAll(super.getExpandMacroMap());
    return result;
  }

  @NotNull
  @Override
  public ReplacePathToMacroMap getReplacePathMap() {
    final ReplacePathToMacroMap result = super.getReplacePathMap();
    if (!myModule.isDisposed()) {
      final String modulePath = PathMacroUtil.getModuleDir(myModule.getModuleFilePath());
      addFileHierarchyReplacements(result, PathMacroUtil.MODULE_DIR_MACRO_NAME, modulePath, PathMacroUtil.getUserHomePath());
    }
    return result;
  }
}
