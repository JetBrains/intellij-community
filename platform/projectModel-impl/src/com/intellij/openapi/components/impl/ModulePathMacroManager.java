// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

public final class ModulePathMacroManager extends PathMacroManager {
  private final Module myModule;

  public ModulePathMacroManager(@NotNull Module module) {
    super(PathMacros.getInstance());
    myModule = module;
  }

  @Override
  public @NotNull ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = super.getExpandMacroMap();
    addFileHierarchyReplacements(result, PathMacroUtil.MODULE_DIR_MACRO_NAME, PathMacroUtil.getModuleDir(myModule.getModuleFilePath()));
    return result;
  }

  @Override
  public @NotNull ReplacePathToMacroMap computeReplacePathMap() {
    ReplacePathToMacroMap result = super.computeReplacePathMap();
    String modulePath = PathMacroUtil.getModuleDir(myModule.getModuleFilePath());
    addFileHierarchyReplacements(result, PathMacroUtil.MODULE_DIR_MACRO_NAME, modulePath, PathMacroUtil.getUserHomePath());
    return result;
  }
}