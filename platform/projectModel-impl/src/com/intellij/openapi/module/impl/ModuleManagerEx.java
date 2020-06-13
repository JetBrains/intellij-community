// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
public abstract class ModuleManagerEx extends ModuleManager {
  public static final String IML_EXTENSION = ".iml";
  public static final String MODULE_GROUP_SEPARATOR = "/";

  @ApiStatus.Experimental
  public abstract Collection<ModulePath> getFailedModulePaths();

  public static ModuleManagerEx getInstanceEx(@NotNull Project project) {
    return (ModuleManagerEx)getInstance(project);
  }
}
