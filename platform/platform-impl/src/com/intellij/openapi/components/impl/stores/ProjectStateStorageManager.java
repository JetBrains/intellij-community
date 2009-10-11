/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorageOperation;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Map;

class ProjectStateStorageManager extends StateStorageManagerImpl {
  protected final Project myProject;
  @NonNls protected static final String ROOT_TAG_NAME = "project";

  public ProjectStateStorageManager(final TrackingPathMacroSubstitutor macroSubstitutor, Project project) {
    super(macroSubstitutor, ROOT_TAG_NAME, project, project.getPicoContainer());
    myProject = project;
  }

  protected XmlElementStorage.StorageData createStorageData(String storageSpec) {
    if (storageSpec.equals(ProjectStoreImpl.PROJECT_FILE_STORAGE)) return createIprStorageData();
    if (storageSpec.equals(ProjectStoreImpl.WS_FILE_STORAGE)) return createWsStorageData();
    return new ProjectStoreImpl.ProjectStorageData(ROOT_TAG_NAME, myProject);
  }

  public XmlElementStorage.StorageData createWsStorageData() {
    return new ProjectStoreImpl.WsStorageData(ROOT_TAG_NAME, myProject);
  }

  public XmlElementStorage.StorageData createIprStorageData() {
    return new ProjectStoreImpl.IprStorageData(ROOT_TAG_NAME, myProject);
  }

  protected String getOldStorageSpec(Object component, final String componentName, final StateStorageOperation operation) throws
                                                                                                                              StateStorage.StateStorageException {
    final ComponentConfig config = myProject.getConfig(component.getClass());
    assert config != null : "Couldn't find old storage for " + component.getClass().getName();

    String macro = ProjectStoreImpl.PROJECT_FILE_MACRO;

    final boolean workspace = isWorkspace(config.options);

    if (workspace) {
      macro = ProjectStoreImpl.WS_FILE_MACRO;
    }

    String name = "$" + macro + "$";

    StateStorage storage = getFileStateStorage(name);

    if (operation == StateStorageOperation.READ && storage != null && workspace && !storage.hasState(component, componentName, Element.class, false)) {
      name = "$" + ProjectStoreImpl.PROJECT_FILE_MACRO + "$";
    }

    return name;
  }

  protected String getVersionsFilePath() {
    return PathManager.getConfigPath() + "/componentVersions/" + "project" + myProject.getLocationHash() + ".xml";
  }

  private static boolean isWorkspace(final Map options) {
    return options != null && Boolean.parseBoolean((String)options.get(ProjectStoreImpl.OPTION_WORKSPACE));
  }
}
