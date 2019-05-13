// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.roots.ExternalProjectSystemRegistry;
import com.intellij.openapi.roots.ProjectModelElement;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.roots.TestModuleProperties;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
@State(name = "TestModuleProperties")
public class TestModulePropertiesImpl extends TestModuleProperties implements PersistentStateComponent<TestModulePropertiesImpl.TestModulePropertiesState>,
                                                                              ProjectModelElement {
  private final ModulePointerManager myModulePointerManager;
  private ModulePointer myProductionModulePointer;
  private final Module myModule;

  public TestModulePropertiesImpl(@NotNull Module module, @NotNull ModulePointerManager modulePointerManager) {
    myModule = module;
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
  public void loadState(@NotNull TestModulePropertiesState state) {
    setProductionModuleName(state.moduleName);
  }

  @Nullable
  @Override
  public ProjectModelExternalSource getExternalSource() {
    return ExternalProjectSystemRegistry.getInstance().getExternalSource(myModule);
  }

  public static class TestModulePropertiesState {
    @Attribute("production-module")
    public String moduleName;
  }
}
