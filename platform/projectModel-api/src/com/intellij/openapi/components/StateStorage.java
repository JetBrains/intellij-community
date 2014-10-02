/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public interface StateStorage {
  // app storage files changed
  Topic<Listener> STORAGE_TOPIC = new Topic<Listener>("STORAGE_LISTENER", Listener.class, Topic.BroadcastDirection.NONE);
  // project storage files changes (project or modules, it is reason why we use broadcast TO_PARENT - to be notified when some module storage file changed
  //  even if listen only project message bus)
  Topic<Listener> PROJECT_STORAGE_TOPIC = new Topic<Listener>("PROJECT_STORAGE_LISTENER", Listener.class, Topic.BroadcastDirection.NONE);

  @Nullable
  <T> T getState(@Nullable Object component, @NotNull String componentName, @NotNull Class<T> stateClass, @Nullable T mergeInto) throws StateStorageException;

  boolean hasState(@Nullable Object component, @NotNull String componentName, final Class<?> aClass, final boolean reloadData) throws StateStorageException;

  @Nullable
  ExternalizationSession startExternalization();

  /**
   * return null if nothing to save
   */
  @Nullable
  SaveSession startSave(@NotNull ExternalizationSession externalizationSession);

  /**
   * Get changed component names
   */
  void analyzeExternalChangesAndUpdateIfNeed(@NotNull Collection<Pair<VirtualFile, StateStorage>> changedFiles, @NotNull Set<String> result);

  interface ExternalizationSession {
    void setState(@NotNull Object component, @NotNull String componentName, @NotNull Object state, @Nullable Storage storageSpec);
  }

  interface SaveSession {
    void save();
  }

  interface Listener {
    void storageFileChanged(@NotNull VirtualFileEvent event, @NotNull StateStorage storage);
  }
}
