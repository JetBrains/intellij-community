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

import com.intellij.openapi.components.*;
import com.intellij.openapi.options.StreamProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.fs.IFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author mike
 */
public interface StateStorageManager {
  void addMacro(String macro, String expansion);

  @Nullable
  TrackingPathMacroSubstitutor getMacroSubstitutor();

  @Nullable
  StateStorage getStateStorage(@NotNull Storage storageSpec) throws StateStorageException;

  @Nullable
  StateStorage getFileStateStorage(@NotNull String fileSpec);

  Collection<String> getStorageFileNames();

  void clearStateStorage(@NotNull String file);

  @NotNull
  ExternalizationSession startExternalization();

  @NotNull
  SaveSession startSave(@NotNull ExternalizationSession externalizationSession);

  void finishSave(@NotNull SaveSession saveSession);

  @Nullable
  StateStorage getOldStorage(Object component, String componentName, StateStorageOperation operation) throws StateStorageException;

  @Nullable
  String expandMacros(String file);

  @Deprecated
  void registerStreamProvider(@SuppressWarnings("deprecation") StreamProvider streamProvider, final RoamingType type);

  void setStreamProvider(@Nullable com.intellij.openapi.components.impl.stores.StreamProvider streamProvider);

  @Nullable
  com.intellij.openapi.components.impl.stores.StreamProvider getStreamProvider();

  void reset();

  interface ExternalizationSession {
    void setState(@NotNull Storage[] storageSpecs, @NotNull Object component, String componentName, @NotNull Object state) throws StateStorageException;
    void setStateInOldStorage(@NotNull Object component, @NotNull String componentName, @NotNull Object state) throws StateStorageException;
  }

  interface SaveSession {
    //returns set of component which were changed, null if changes are much more than just component state.
    @Nullable
    Set<String> analyzeExternalChanges(@NotNull Set<Pair<VirtualFile, StateStorage>> files);

    @NotNull
    List<IFile> getAllStorageFilesToSave() throws StateStorageException;

    @NotNull
    List<IFile> getAllStorageFiles();

    void save() throws StateStorageException;
  }
}