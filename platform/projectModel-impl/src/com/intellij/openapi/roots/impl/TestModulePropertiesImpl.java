/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.roots.TestModuleProperties;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
@State(name = "TestModuleProperties")
public class TestModulePropertiesImpl extends TestModuleProperties implements PersistentStateComponent<TestModulePropertiesImpl.TestModulePropertiesState> {
  private final ModulePointerManager myModulePointerManager;
  private ModulePointer myProductionModulePointer;

  public TestModulePropertiesImpl(@NotNull ModulePointerManager modulePointerManager) {
    myModulePointerManager = modulePointerManager;
  }

  @Nullable
  @Override
  public String getProductionModuleName() {
    return myProductionModulePointer != null ? myProductionModulePointer.getModuleName() : null;
  }

  @Nullable
  @Override
  public Module getProductionModule() {
    return myProductionModulePointer != null ? myProductionModulePointer.getModule() : null;
  }

  @Override
  public void setProductionModuleName(@Nullable String moduleName) {
    myProductionModulePointer = moduleName != null ? myModulePointerManager.create(moduleName) : null;
  }

  @Nullable
  @Override
  public TestModulePropertiesState getState() {
    TestModulePropertiesState state = new TestModulePropertiesState();
    state.moduleName = getProductionModuleName();
    return state;
  }

  @Override
  public void loadState(TestModulePropertiesState state) {
    setProductionModuleName(state.moduleName);
  }

  public static class TestModulePropertiesState {
    @Attribute("production-module")
    public String moduleName;
  }
}
