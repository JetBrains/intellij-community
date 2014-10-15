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
package com.intellij.openapi.components.store;

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.impl.stores.DefaultStateSerializer;
import com.intellij.openapi.components.impl.stores.StorageDataBase;
import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class StateStorageBase<T extends StorageDataBase> implements StateStorage {
  protected static final Logger LOG = Logger.getInstance(StateStorageBase.class);

  private boolean mySavingDisabled = false;
  protected final TrackingPathMacroSubstitutor myPathMacroSubstitutor;

  protected StateStorageBase(@Nullable TrackingPathMacroSubstitutor trackingPathMacroSubstitutor) {
    myPathMacroSubstitutor = trackingPathMacroSubstitutor;
  }

  @Override
  @Nullable
  public final <S> S getState(Object component, @NotNull String componentName, @NotNull Class<S> stateClass, @Nullable S mergeInto) throws StateStorageException {
    return DefaultStateSerializer.deserializeState(getStateAndArchive(getStorageData(), componentName), stateClass, mergeInto);
  }

  @Nullable
  protected abstract Element getStateAndArchive(@NotNull T storageData, @NotNull String componentName);

  @Override
  public final boolean hasState(@Nullable Object component, @NotNull String componentName, Class<?> aClass, boolean reloadData) {
    return getStorageData(reloadData).hasState(componentName);
  }

  @NotNull
  public final T getStorageData() {
    return getStorageData(false);
  }

  protected abstract T getStorageData(boolean reloadData);

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