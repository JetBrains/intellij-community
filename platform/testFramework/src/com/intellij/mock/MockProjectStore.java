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
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author peter
 */
public class MockProjectStore implements IProjectStore {
  public boolean checkVersion() {
    throw new UnsupportedOperationException("Method checkVersion is not yet implemented in " + getClass().getName());
  }

  public void setProjectFilePath(final String filePath) {
    throw new UnsupportedOperationException("Method setProjectFilePath is not yet implemented in " + getClass().getName());
  }

  public void reinitComponents(Set<String> componentNames, boolean reloadData) {
    throw new UnsupportedOperationException("Method reinitComponents is not yet implemented in " + getClass().getName());
  }

  public boolean isReloadPossible(Set<String> componentNames) {
    throw new UnsupportedOperationException("Method isReloadPossible is not yet implemented in " + getClass().getName());
  }

  public TrackingPathMacroSubstitutor[] getSubstitutors() {
    return new TrackingPathMacroSubstitutor[0];
  }

  @Nullable
  public VirtualFile getProjectBaseDir() {
    throw new UnsupportedOperationException("Method getProjectBaseDir is not yet implemented in " + getClass().getName());
  }//------ This methods should be got rid of

  public String getLocation() {
    throw new UnsupportedOperationException("Method getLocation not implemented in " + getClass());
  }

  @NotNull
  public String getProjectName() {
    throw new UnsupportedOperationException("Method getProjectName not implemented in " + getClass());
  }

  @NotNull
  public StorageScheme getStorageScheme() {
    throw new UnsupportedOperationException("Method getStorageScheme is not yet implemented in " + getClass().getName());
  }

  public void loadProject() throws IOException, JDOMException, InvalidDataException {
    throw new UnsupportedOperationException("Method loadProject is not yet implemented in " + getClass().getName());
  }

  @Nullable
  public VirtualFile getProjectFile() {
    throw new UnsupportedOperationException("Method getProjectFile is not yet implemented in " + getClass().getName());
  }

  @Nullable
  public VirtualFile getWorkspaceFile() {
    throw new UnsupportedOperationException("Method getWorkspaceFile is not yet implemented in " + getClass().getName());
  }

  public void loadProjectFromTemplate(ProjectImpl project) {
    throw new UnsupportedOperationException("Method loadProjectFromTemplate is not yet implemented in " + getClass().getName());
  }

  @NotNull
  public String getProjectFileName() {
    throw new UnsupportedOperationException("Method getProjectFileName is not yet implemented in " + getClass().getName());
  }

  @NotNull
  public String getProjectFilePath() {
    return null;
  }

  public void setUsedMacros(@NotNull Collection<String> macros) {
  }

  public Set<String> getMacroTrackingSet() {
    return new TreeSet<String>();
  }

  public void initStore() {
    throw new UnsupportedOperationException("Method initStore is not yet implemented in " + getClass().getName());
  }

  public String initComponent(Object component, final boolean service) {
    throw new UnsupportedOperationException("Method initComponent is not yet implemented in " + getClass().getName());
  }

  public void commit() {
    throw new UnsupportedOperationException("Method commit is not yet implemented in " + getClass().getName());
  }

  public boolean save() throws IOException {
    throw new UnsupportedOperationException("Method save is not yet implemented in " + getClass().getName());
  }

  public void load() throws IOException {
    throw new UnsupportedOperationException("Method load is not yet implemented in " + getClass().getName());
  }

  public Collection<String> getUsedMacros() {
    throw new UnsupportedOperationException("Method getUsedMacros not implemented in " + getClass());
  }

  @NotNull
  public SaveSession startSave() throws IOException {
    throw new UnsupportedOperationException("Method startSave not implemented in " + getClass());
  }

  public List<VirtualFile> getAllStorageFilesToSave(final boolean includingSubStructures) {
    throw new UnsupportedOperationException("Method getAllStorageFilesToSave is not yet implemented in " + getClass().getName());
  }

  @Nullable
  public String getPresentableUrl() {
    throw new UnsupportedOperationException("Method getPresentableUrl not implemented in " + getClass());
  }

  public boolean reload(final Set<Pair<VirtualFile,StateStorage>> changedFiles) {
    throw new UnsupportedOperationException("Method reload not implemented in " + getClass());
  }

  public StateStorageManager getStateStorageManager() {
    throw new UnsupportedOperationException("Method getStateStorageManager not implemented in " + getClass());
  }

  public boolean isSaving() {
    return false;
  }
}
