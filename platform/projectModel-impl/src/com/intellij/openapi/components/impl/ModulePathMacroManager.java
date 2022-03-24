// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.serviceContainer.NonInjectable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.Map;
import java.util.function.Supplier;

public class ModulePathMacroManager extends PathMacroManager {
  private final @NotNull Supplier<@Nullable @SystemIndependent String> myProjectFilePathPointer;
  private final @NotNull Supplier<@NotNull @SystemIndependent String> myModuleDirPointer;

  public ModulePathMacroManager(@NotNull Module module) {
    super(PathMacros.getInstance());
    myProjectFilePathPointer = module.getProject()::getProjectFilePath;
    myModuleDirPointer = module::getModuleFilePath;
  }

  @NonInjectable
  private ModulePathMacroManager(@NotNull Supplier<@Nullable @SystemIndependent String> projectFilePathPointer,
                                 @NotNull Supplier<@NotNull @SystemIndependent String> moduleDirPointer) {
    super(PathMacros.getInstance());
    myProjectFilePathPointer = projectFilePathPointer;
    myModuleDirPointer = moduleDirPointer;
  }

  @Override
  public @NotNull ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = super.getExpandMacroMap();
    addFileHierarchyReplacements(result, PathMacroUtil.MODULE_DIR_MACRO_NAME, PathMacroUtil.getModuleDir(myModuleDirPointer.get()));
    String projectFile = myProjectFilePathPointer.get();
    if (projectFile != null) {
      for (Map.Entry<String, String> entry : ProjectWidePathMacroContributor.getAllMacros(projectFile).entrySet()) {
        result.addMacroExpand(entry.getKey(), entry.getValue());
      }
    }
    return result;
  }

  @Override
  public @NotNull ReplacePathToMacroMap computeReplacePathMap() {
    ReplacePathToMacroMap result = super.computeReplacePathMap();
    addFileHierarchyReplacements(result, PathMacroUtil.MODULE_DIR_MACRO_NAME, PathMacroUtil.getModuleDir(myModuleDirPointer.get()), PathMacroUtil.getUserHomePath());
    return result;
  }

  public void onImlFileMoved() {
    resetCachedReplacePathMap();
  }

  public static ModulePathMacroManager createInstance(@NotNull Supplier<@Nullable @SystemIndependent String> projectFilePathPointer,
                                                      @NotNull Supplier<@NotNull @SystemIndependent String> moduleDirPointer) {
    return new ModulePathMacroManager(projectFilePathPointer, moduleDirPointer);
  }
}
