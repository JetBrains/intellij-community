/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.module;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.devkit.model.impl.JpsDevKitModelSerializerExtension;

/**
 * This is temporary solution to store runtime resource roots in a separate component to ensure that they won't be deleted if the project
 * is opened in previous IDEA builds.
 *
 * @author nik
 */
@State(
  name = JpsDevKitModelSerializerExtension.RUNTIME_RESOURCES_COMPONENT_NAME,
  storages = {@Storage(file = StoragePathMacros.MODULE_FILE)}
)
public class RuntimeResourcesComponent implements ModuleComponent, PersistentStateComponent<JpsDevKitModelSerializerExtension.RuntimeResourceListState> {
  private final ModuleRootManager myRootManager;

  public RuntimeResourcesComponent(ModuleRootManager rootManager) {
    myRootManager = rootManager;
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {

  }

  @Override
  public void moduleAdded() {

  }

  @Override
  public void initComponent() {

  }

  @Override
  public void disposeComponent() {

  }

  @Nullable
  @Override
  public JpsDevKitModelSerializerExtension.RuntimeResourceListState getState() {
    RuntimeResourcesConfigurationImpl extension = getExtension();
    return extension.isLoadedByExtension() ? new JpsDevKitModelSerializerExtension.RuntimeResourceListState() : extension.getState();
  }

  private RuntimeResourcesConfigurationImpl getExtension() {
    return (RuntimeResourcesConfigurationImpl)myRootManager.getModuleExtension(RuntimeResourcesConfiguration.class);
  }

  @Override
  public void loadState(JpsDevKitModelSerializerExtension.RuntimeResourceListState state) {
    getExtension().loadState(state);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return JpsDevKitModelSerializerExtension.RUNTIME_RESOURCES_COMPONENT_NAME;
  }
}
