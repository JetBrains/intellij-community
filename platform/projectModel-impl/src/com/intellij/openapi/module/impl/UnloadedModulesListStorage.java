/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.module.impl;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
@State(name = "UnloadedModulesList", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class UnloadedModulesListStorage implements PersistentStateComponent<UnloadedModulesListStorage> {
  private List<String> myModuleNames = new ArrayList<>();

  static UnloadedModulesListStorage getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, UnloadedModulesListStorage.class);
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
  public void loadState(UnloadedModulesListStorage state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
