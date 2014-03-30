/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.fs.IFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface IComponentStore {
  void initComponent(@NotNull Object component, boolean service);
  void reinitComponents(@NotNull Set<String> componentNames, boolean reloadData);
  boolean isReloadPossible(@NotNull Set<String> componentNames);

  void load() throws IOException, StateStorageException;
  boolean isSaving();

  @NotNull
  StateStorageManager getStateStorageManager();


  class SaveCancelledException extends IOException {
    public SaveCancelledException() {
    }

    public SaveCancelledException(final String s) {
      super(s);
    }
  }

  //todo:remove throws
  @NotNull
  SaveSession startSave() throws IOException;

  interface SaveSession {
    @NotNull
    List<IFile> getAllStorageFilesToSave(final boolean includingSubStructures) throws IOException;
    @NotNull
    SaveSession save() throws IOException;
    void finishSave();
    void reset();

    @Nullable
    Set<String> analyzeExternalChanges(@NotNull Set<Pair<VirtualFile,StateStorage>> changedFiles);

    @NotNull
    List<IFile> getAllStorageFiles(final boolean includingSubStructures);
  }

}
