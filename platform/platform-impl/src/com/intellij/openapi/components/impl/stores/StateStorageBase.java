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

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public abstract class StateStorageBase<T> implements StateStorage {
  protected static final Logger LOG = Logger.getInstance(StateStorageBase.class);

  private boolean mySavingDisabled = false;

  protected final AtomicReference<T> storageDataRef = new AtomicReference<T>();

  @Override
  @Nullable
  public final <S> S getState(Object component, @NotNull String componentName, @NotNull Class<S> stateClass, @Nullable S mergeInto, boolean reload) {
    return deserializeState(getStateAndArchive(getStorageData(reload), component, componentName), stateClass, mergeInto);
  }

  @Override
  public final <S> S getState(@Nullable Object component, @NotNull String componentName, @NotNull Class<S> stateClass) {
    return getState(component, componentName, stateClass, null, false);
  }

  @Nullable
  protected <S> S deserializeState(@Nullable Element serializedState, @NotNull Class <S> stateClass, @Nullable S mergeInto) {
    return DefaultStateSerializer.deserializeState(serializedState, stateClass, mergeInto);
  }

  @Nullable
  protected abstract Element getStateAndArchive(@NotNull T storageData, Object component, @NotNull String componentName);

  protected abstract boolean hasState(@NotNull T storageData, @NotNull String componentName);

  @Override
  public final boolean hasState(@NotNull String componentName, boolean reloadData) {
    return hasState(getStorageData(reloadData), componentName);
  }

  @NotNull
  public final T getStorageData() {
    return getStorageData(false);
  }

  @NotNull
  protected final T getStorageData(boolean reload) {
    final T storageData = storageDataRef.get();
    if (storageData != null && !reload) {
      return storageData;
    }

    T newStorageData = loadData();
    if (storageDataRef.compareAndSet(storageData, newStorageData)) {
      return newStorageData;
    }
    else {
      return getStorageData(false);
    }
  }

  protected abstract T loadData();

  public final void disableSaving() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Disabled saving for " + toString());
    }
    mySavingDisabled = true;
  }

  public final void enableSaving() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Enabled saving " + toString());
    }
    mySavingDisabled = false;
  }

  protected final boolean checkIsSavingDisabled() {
    if (mySavingDisabled && LOG.isDebugEnabled()) {
      LOG.debug("Saving disabled for " + toString());
    }
    return mySavingDisabled;
  }
}