// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl.stores;

import com.intellij.configurationStore.StateStorageManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Set;

public interface IComponentStore {
  void setPath(@NotNull @SystemIndependent String path);

  void initComponent(@NotNull Object component, boolean service);

  void initPersistencePlainComponent(@NotNull Object component, @NotNull String key);

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
  void saveApplicationComponent(@NotNull PersistentStateComponent<?> component);
}
