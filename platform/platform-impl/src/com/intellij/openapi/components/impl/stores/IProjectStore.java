/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

/**
 * @see com.intellij.openapi.project.ex.ProjectEx#getStateStore()
 */
public interface IProjectStore extends IComponentStore {
  boolean checkVersion();

  void setProjectFilePath(@NotNull String filePath);

  @Nullable
  VirtualFile getProjectBaseDir();

  @Nullable
  String getProjectBasePath();

  @NotNull
  String getProjectName();

  TrackingPathMacroSubstitutor[] getSubstitutors();

  @NotNull
  StorageScheme getStorageScheme();

  @Nullable
  String getPresentableUrl();

  boolean reload(@NotNull Set<Pair<VirtualFile,StateStorage>> changedFiles) throws StateStorageException, IOException;

  //------ This methods should be got rid of
  /** @deprecated to remove in IDEA 14 */
  void loadProject() throws IOException, JDOMException, InvalidDataException, StateStorageException;

  @Nullable
  VirtualFile getProjectFile();

  @Nullable
  VirtualFile getWorkspaceFile();

  void loadProjectFromTemplate(@NotNull ProjectImpl project);

  @NotNull
  String getProjectFilePath();
}
