/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.impl.ProjectImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Map;

class ProjectStateStorageManager extends StateStorageManagerImpl {
  protected final ProjectImpl myProject;
  @NonNls protected static final String ROOT_TAG_NAME = "project";

  public ProjectStateStorageManager(final TrackingPathMacroSubstitutor macroSubstitutor, ProjectImpl project) {
    super(macroSubstitutor, ROOT_TAG_NAME, project, project.getPicoContainer());
    myProject = project;
  }

  @Override
  protected StorageData createStorageData(String storageSpec) {
    if (storageSpec.equals(StoragePathMacros.PROJECT_FILE)) return createIprStorageData();
    if (storageSpec.equals(StoragePathMacros.WORKSPACE_FILE)) return createWsStorageData();
    return new ProjectStoreImpl.ProjectStorageData(ROOT_TAG_NAME, myProject);
  }

  public StorageData createWsStorageData() {
    return new ProjectStoreImpl.WsStorageData(ROOT_TAG_NAME, myProject);
  }

  public StorageData createIprStorageData() {
    return new ProjectStoreImpl.IprStorageData(ROOT_TAG_NAME, myProject);
  }

  @Override
  protected String getOldStorageSpec(Object component, final String componentName, final StateStorageOperation operation) throws
                                                                                                                          StateStorageException {
    final ComponentConfig config = myProject.getConfig(component.getClass());
    assert config != null : "Couldn't find old storage for " + component.getClass().getName();

    final boolean workspace = isWorkspace(config.options);
    String macro = StoragePathMacros.getMacroName(workspace ? StoragePathMacros.WORKSPACE_FILE : StoragePathMacros.PROJECT_FILE);

    String name = "$" + macro + "$";

    StateStorage storage = getFileStateStorage(name);

    if (operation == StateStorageOperation.READ && storage != null && workspace && !storage.hasState(component, componentName, Element.class, false)) {
      name = StoragePathMacros.PROJECT_FILE;
    }

    return name;
  }

  @Override
  protected String getVersionsFilePath() {
    return PathManager.getConfigPath() + "/componentVersions/" + "project" + myProject.getLocationHash() + ".xml";
  }

  private static boolean isWorkspace(final Map options) {
    return options != null && Boolean.parseBoolean((String)options.get(ProjectStoreImpl.OPTION_WORKSPACE));
  }
}
