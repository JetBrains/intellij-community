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
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public interface StorageAnnotationsDefaultValues {
  class NullStateStorage implements StateStorage {
    @Override
    @Nullable
    public <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto)
    throws StateStorageException {
      throw new UnsupportedOperationException("Method getState is not supported in " + getClass());
    }

    @Override
    public boolean hasState(final Object component, final String componentName, final Class<?> aClass, final boolean reloadData) throws StateStorageException {
      throw new UnsupportedOperationException("Method hasState not implemented in " + getClass());
    }

    public void save() throws StateStorageException {
      throw new UnsupportedOperationException("Method save is not supported in " + getClass());
    }

    @Override
    @NotNull
    public ExternalizationSession startExternalization() {
      throw new UnsupportedOperationException("Method startExternalization not implemented in " + getClass());
    }

    @Override
    @NotNull
    public SaveSession startSave(@NotNull ExternalizationSession externalizationSession) {
      throw new UnsupportedOperationException("Method startSave not implemented in " + getClass());
    }

    @Override
    public void finishSave(@NotNull SaveSession saveSession) {
      throw new UnsupportedOperationException("Method finishSave not implemented in " + getClass());
    }

    @Override
    public void reload(@NotNull final Set<String> changedComponents) throws StateStorageException {
      throw new UnsupportedOperationException("Method reload not implemented in " + getClass());
    }

  }

  class NullStateStorageChooser implements StateStorageChooser {
    @Override
    public Storage[] selectStorages(Storage[] storages, Object component, final StateStorageOperation operation) {
      throw new UnsupportedOperationException("Method selectStorages is not supported in " + getClass());
    }
  }

  class NullStateSplitter implements StateSplitter {
    @Override
    public List<Pair<Element, String>> splitState(Element e) {
      throw new UnsupportedOperationException("Method splitState not implemented in " + getClass());
    }

    @Override
    public void mergeStatesInto(final Element target, final Element[] elements) {
      throw new UnsupportedOperationException("Method mergeStatesInto not implemented in " + getClass());
    }
  }
}
