// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

public interface StateStorage {
  /**
   * You can call this method only once.
   * If state exists and not archived - not-null result.
   * If doesn't exists or archived - null result.
   */
  @Nullable
  <T> T getState(@Nullable Object component, @NotNull String componentName, @NotNull Class<T> stateClass, @Nullable T mergeInto, boolean reload);

  boolean hasState(@NotNull String componentName, boolean reloadData);

  @Nullable
  SaveSessionProducer createSaveSessionProducer();

  /**
   * Get changed component names
   */
  void analyzeExternalChangesAndUpdateIfNeed(@NotNull Set<String> componentNames);

  @NotNull
  default StateStorageChooserEx.Resolution getResolution(@NotNull PersistentStateComponent<?> component, @NotNull StateStorageOperation operation) {
    return StateStorageChooserEx.Resolution.DO;
  }

  interface SaveSessionProducer {
    default void setState(@Nullable Object component, @NotNull String componentName, @NotNull Object state) {
    }

    /**
     * return null if nothing to save
     */
    @Nullable
    SaveSession createSaveSession();
  }

  interface SaveSession {
    void save() throws IOException;
  }
}