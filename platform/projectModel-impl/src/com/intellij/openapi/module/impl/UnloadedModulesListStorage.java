/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.module.impl;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
@State(name = "UnloadedModulesList", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class UnloadedModulesListStorage implements PersistentStateComponent<UnloadedModulesListStorage> {
  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false, elementTag = "module", elementValueAttribute = "name")
  public List<String> moduleNames = new ArrayList<>();

  static UnloadedModulesListStorage getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, UnloadedModulesListStorage.class);
  }

  public List<String> getUnloadedModuleNames() {
    return Collections.unmodifiableList(moduleNames);
  }

  public void setUnloadedModuleNames(List<String> names) {
    moduleNames.clear();
    moduleNames.addAll(names);
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
