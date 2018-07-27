// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

public class ModulePathMacroManager extends PathMacroManager {
  private final Module myModule;

  public ModulePathMacroManager(@NotNull PathMacros pathMacros, @NotNull Module module) {
    super(pathMacros);

    myModule = module;
  }

  @NotNull
  @Override
  public ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = super.getExpandMacroMap();
    addFileHierarchyReplacements(result, PathMacroUtil.MODULE_DIR_MACRO_NAME, PathMacroUtil.getModuleDir(myModule.getModuleFilePath()));
    return result;
  }

  @NotNull
  @Override
  public ReplacePathToMacroMap getReplacePathMap() {
    final ReplacePathToMacroMap result = super.getReplacePathMap();
    final String modulePath = PathMacroUtil.getModuleDir(myModule.getModuleFilePath());
    addFileHierarchyReplacements(result, PathMacroUtil.MODULE_DIR_MACRO_NAME, modulePath, PathMacroUtil.getUserHomePath());
    return result;
  }
}
