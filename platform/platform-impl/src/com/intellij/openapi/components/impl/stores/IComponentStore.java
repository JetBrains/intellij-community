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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Set;

public interface IComponentStore {
  /**
   * @param path System-independent path.
   */
  void setPath(@NotNull String path);

  void initComponent(@NotNull Object component, boolean service);

  void reloadStates(@NotNull Set<String> componentNames, @NotNull MessageBus messageBus);

  void reloadState(@NotNull Class<? extends PersistentStateComponent<?>> componentClass);

  boolean isReloadPossible(@NotNull Set<String> componentNames);

  @NotNull
  StateStorageManager getStateStorageManager();

  class SaveCancelledException extends RuntimeException {
    public SaveCancelledException() {
    }
  }

  void save(@NotNull List<Pair<StateStorage.SaveSession, VirtualFile>> readonlyFiles);

  @TestOnly
  void saveApplicationComponent(@NotNull Object component);
}
