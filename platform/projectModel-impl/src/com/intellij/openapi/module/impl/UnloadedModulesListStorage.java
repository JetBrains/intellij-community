// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.platform.workspace.jps.UnloadedModulesNameHolder;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.annotations.RequiresBlockingContext;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@ApiStatus.Internal
@State(name = "UnloadedModulesList", storages = @Storage(value = StoragePathMacros.WORKSPACE_FILE, useSaveThreshold = ThreeState.NO))
public final class UnloadedModulesListStorage implements PersistentStateComponent<UnloadedModulesListStorage> {
  @Property(surroundWithTag = false)
  @XCollection(elementName = "module", valueAttributeName = "name")
  private final Set<String> moduleNames = new HashSet<>();

  // Do not call directly. Use ModuleManager
  @ApiStatus.Internal
  @RequiresBlockingContext
  public static UnloadedModulesListStorage getInstance(@NotNull Project project) {
    return project.getService(UnloadedModulesListStorage.class);
  }

  @Transient
  @ApiStatus.Internal
  public @NotNull UnloadedModulesNameHolder getUnloadedModuleNameHolder() {
    return new UnloadedModulesNameHolderImpl(moduleNames);
  }

  public void setUnloadedModuleNames(@NotNull Collection<String> value) {
    this.moduleNames.clear();
    this.moduleNames.addAll(value);
  }

  public void addUnloadedModuleNames(@NotNull Collection<String> value) {
    this.moduleNames.addAll(value);
  }

  @Override
  public @NotNull UnloadedModulesListStorage getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull UnloadedModulesListStorage state) {
    moduleNames.clear();
    moduleNames.addAll(state.moduleNames);
  }
}
