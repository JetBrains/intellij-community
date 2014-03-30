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
package com.intellij.mock;

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.components.impl.stores.StateStorageManager;
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
 * @author peter
 */
public class MockProjectStore implements IProjectStore {
  @Override
  public boolean checkVersion() {
    throw new UnsupportedOperationException("Method checkVersion is not yet implemented in " + getClass().getName());
  }

  @Override
  public void setProjectFilePath(@NotNull final String filePath) {
    throw new UnsupportedOperationException("Method setProjectFilePath is not yet implemented in " + getClass().getName());
  }

  @Override
  public void reinitComponents(@NotNull Set<String> componentNames, boolean reloadData) {
    throw new UnsupportedOperationException("Method reinitComponents is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean isReloadPossible(@NotNull Set<String> componentNames) {
    throw new UnsupportedOperationException("Method isReloadPossible is not yet implemented in " + getClass().getName());
  }

  @Override
  public TrackingPathMacroSubstitutor[] getSubstitutors() {
    return new TrackingPathMacroSubstitutor[0];
  }

  @Override
  public VirtualFile getProjectBaseDir() {
    throw new UnsupportedOperationException("Method getProjectBaseDir is not yet implemented in " + getClass().getName());
  }

  @Override
  public String getProjectBasePath() {
    throw new UnsupportedOperationException("Method getProjectBasePath is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public String getProjectName() {
    throw new UnsupportedOperationException("Method getProjectName not implemented in " + getClass());
  }

  @Override
  @NotNull
  public StorageScheme getStorageScheme() {
    throw new UnsupportedOperationException("Method getStorageScheme is not yet implemented in " + getClass().getName());
  }

  @Override
  public void loadProject() throws IOException, JDOMException, InvalidDataException {
    throw new UnsupportedOperationException("Method loadProject is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public VirtualFile getProjectFile() {
    throw new UnsupportedOperationException("Method getProjectFile is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public VirtualFile getWorkspaceFile() {
    throw new UnsupportedOperationException("Method getWorkspaceFile is not yet implemented in " + getClass().getName());
  }

  @Override
  public void loadProjectFromTemplate(@NotNull ProjectImpl project) {
    throw new UnsupportedOperationException("Method loadProjectFromTemplate is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public String getProjectFilePath() {
    throw new UnsupportedOperationException("Method getProjectFilePath is not yet implemented in " + getClass().getName());
  }

  @Override
  public void initComponent(@NotNull Object component, final boolean service) {
    throw new UnsupportedOperationException("Method initComponent is not yet implemented in " + getClass().getName());
  }

  public void commit() {
    throw new UnsupportedOperationException("Method commit is not yet implemented in " + getClass().getName());
  }

  public boolean save() throws IOException {
    throw new UnsupportedOperationException("Method save is not yet implemented in " + getClass().getName());
  }

  @Override
  public void load() throws IOException {
    throw new UnsupportedOperationException("Method load is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public SaveSession startSave() throws IOException {
    throw new UnsupportedOperationException("Method startSave not implemented in " + getClass());
  }

  @Override
  @Nullable
  public String getPresentableUrl() {
    throw new UnsupportedOperationException("Method getPresentableUrl not implemented in " + getClass());
  }

  @Override
  public boolean reload(@NotNull final Set<Pair<VirtualFile,StateStorage>> changedFiles) {
    throw new UnsupportedOperationException("Method reload not implemented in " + getClass());
  }

  @NotNull
  @Override
  public StateStorageManager getStateStorageManager() {
    throw new UnsupportedOperationException("Method getStateStorageManager not implemented in " + getClass());
  }

  @Override
  public boolean isSaving() {
    return false;
  }
}
