// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public abstract class ModuleManagerEx extends ModuleManager {
  public static final String IML_EXTENSION = ".iml";
  public static final String MODULE_GROUP_SEPARATOR = "/";

  public abstract boolean areModulesLoaded();

  @ApiStatus.Experimental
  public abstract Collection<ModulePath> getFailedModulePaths();

  public static ModuleManagerEx getInstanceEx(@NotNull Project project) {
    return (ModuleManagerEx)getInstance(project);
  }

  public void unloadNewlyAddedModulesIfPossible(@NotNull Set<ModulePath> modulesToLoad,
                                                @NotNull List<UnloadedModuleDescriptionImpl> modulesToUnload) {
  }
}
