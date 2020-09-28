// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.serviceContainer.NonInjectable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.function.Supplier;

public final class ModulePathMacroManager extends PathMacroManager {
  private final Supplier<@SystemIndependent String> myModuleDirPointer;

  public ModulePathMacroManager(@NotNull Module module) {
    super(PathMacros.getInstance());
    myModuleDirPointer = module::getModuleFilePath;
  }

  @NonInjectable
  private ModulePathMacroManager(Supplier<@SystemIndependent String> moduleDirPointer) {
    super(PathMacros.getInstance());
    myModuleDirPointer = moduleDirPointer;
  }

  @Override
  public @NotNull ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = super.getExpandMacroMap();
    addFileHierarchyReplacements(result, PathMacroUtil.MODULE_DIR_MACRO_NAME, PathMacroUtil.getModuleDir(myModuleDirPointer.get()));
    return result;
  }

  @Override
  public @NotNull ReplacePathToMacroMap computeReplacePathMap() {
    ReplacePathToMacroMap result = super.computeReplacePathMap();
    addFileHierarchyReplacements(result, PathMacroUtil.MODULE_DIR_MACRO_NAME, PathMacroUtil.getModuleDir(myModuleDirPointer.get()), PathMacroUtil.getUserHomePath());
    return result;
  }

  public static ModulePathMacroManager createInstance(Supplier<@SystemIndependent String> moduleDirPointer) {
    return new ModulePathMacroManager(moduleDirPointer);
  }
}