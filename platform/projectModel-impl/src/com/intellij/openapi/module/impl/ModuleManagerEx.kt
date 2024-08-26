// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public abstract class ModuleManagerEx extends ModuleManager {
  public static final String IML_EXTENSION = ".iml";
  public static final String MODULE_GROUP_SEPARATOR = "/";

  public abstract boolean areModulesLoaded();

  public static ModuleManagerEx getInstanceEx(@NotNull Project project) {
    return (ModuleManagerEx)getInstance(project);
  }

  public Pair<List<String>, List<String>> calculateUnloadModules(@NotNull MutableEntityStorage builder, @NotNull MutableEntityStorage unloadedEntityBuilder) {
    return new Pair<>(Collections.emptyList(), Collections.emptyList());
  }

  public void updateUnloadedStorage(@NotNull List<String> modulesToLoad, @NotNull List<String> modulesToUnload) { }
}
