// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class ModuleManagerEx extends ModuleManager {
  public static final String IML_EXTENSION = ".iml";
  public static final String MODULE_GROUP_SEPARATOR = "/";

  public abstract boolean areModulesLoaded();

  public static ModuleManagerEx getInstanceEx(@NotNull Project project) {
    return (ModuleManagerEx)getInstance(project);
  }

  public void unloadNewlyAddedModulesIfPossible(@NotNull MutableEntityStorage builder, @NotNull MutableEntityStorage unloadedEntityBuilder) {
  }
}
