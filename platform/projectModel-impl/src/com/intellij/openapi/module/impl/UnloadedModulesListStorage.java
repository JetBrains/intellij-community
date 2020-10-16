// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.ThreeState;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(name = "UnloadedModulesList", storages = @Storage(value = StoragePathMacros.WORKSPACE_FILE, useSaveThreshold = ThreeState.NO))
public class UnloadedModulesListStorage implements PersistentStateComponent<UnloadedModulesListStorage> {
  private List<String> myModuleNames = new ArrayList<>();

  // Do not call directly. Use ModuleManager
  @ApiStatus.Internal
  public static UnloadedModulesListStorage getInstance(@NotNull Project project) {
    return project.getService(UnloadedModulesListStorage.class);
  }

  @Property(surroundWithTag = false)
  @XCollection(elementName = "module", valueAttributeName = "name")
  public List<String> getUnloadedModuleNames() {
    return myModuleNames;
  }

  public void setUnloadedModuleNames(List<String> moduleNames) {
    myModuleNames = moduleNames;
  }

  @Nullable
  @Override
  public UnloadedModulesListStorage getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull UnloadedModulesListStorage state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
