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

package com.intellij.openapi.components;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.util.io.fs.IFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface StateStorage {
  Topic<Listener> STORAGE_TOPIC = new Topic<Listener>("STORAGE_LISTENER", Listener.class, Topic.BroadcastDirection.TO_PARENT);

  @Nullable
  <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto) throws StateStorageException;
  boolean hasState(final Object component, final String componentName, final Class<?> aClass, final boolean reloadData) throws StateStorageException;

  @NotNull
  ExternalizationSession startExternalization();
  @NotNull
  SaveSession startSave(@NotNull ExternalizationSession externalizationSession);
  void finishSave(@NotNull SaveSession saveSession);

  void reload(@NotNull Set<String> changedComponents) throws StateStorageException;

  interface ExternalizationSession {
    void setState(@NotNull Object component, final String componentName, @NotNull Object state, @Nullable final Storage storageSpec) throws StateStorageException;
  }

  interface SaveSession {
    void save() throws StateStorageException;

    @Nullable
    Set<String> analyzeExternalChanges(@NotNull Set<Pair<VirtualFile,StateStorage>> changedFiles);

    @NotNull
    Collection<IFile> getStorageFilesToSave() throws StateStorageException;

    @NotNull
    List<IFile> getAllStorageFiles();
  }

  interface Listener {
    void storageFileChanged(@NotNull VirtualFileEvent event, @NotNull StateStorage storage);
  }
}
