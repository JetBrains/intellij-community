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
package com.intellij.openapi.components;

import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

public interface StateStorage {
  /**
   * @deprecated use StateStorageManager.STORAGE_TOPIC (to be removed in IDEA 16)
   * app storage files changed
   */
  @SuppressWarnings("unused")
  @Deprecated
  Topic<Listener> STORAGE_TOPIC = new Topic<Listener>("STORAGE_LISTENER", Listener.class, Topic.BroadcastDirection.NONE);

  /**
   * @deprecated use StateStorageManager.STORAGE_TOPIC (to be removed in IDEA 16)
   * project storage files changes (project or modules)
   */
  @SuppressWarnings("unused")
  @Deprecated
  Topic<Listener> PROJECT_STORAGE_TOPIC = new Topic<Listener>("PROJECT_STORAGE_LISTENER", Listener.class, Topic.BroadcastDirection.NONE);

  /**
   * You can call this method only once.
   * If state exists and not archived - not-null result.
   * If doesn't exists or archived - null result.
   */
  @Nullable
  <T> T getState(@Nullable Object component, @NotNull String componentName, @NotNull Class<T> stateClass, @Nullable T mergeInto);

  boolean hasState(@NotNull String componentName, boolean reloadData);

  @Nullable
  ExternalizationSession startExternalization();

  /**
   * Get changed component names
   */
  void analyzeExternalChangesAndUpdateIfNeed(@NotNull Set<String> componentNames);

  interface ExternalizationSession {
    void setState(@NotNull Object component, @NotNull String componentName, @NotNull Object state, @Nullable Storage storageSpec);

    /**
     * return null if nothing to save
     */
    @Nullable
    SaveSession createSaveSession();
  }

  interface SaveSession {
    void save() throws IOException;
  }

  interface Listener {
    void storageFileChanged(@NotNull VirtualFileEvent event, @NotNull StateStorage storage);
  }
}
