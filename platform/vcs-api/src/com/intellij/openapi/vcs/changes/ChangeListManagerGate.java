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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * only to be used by {@link ChangeProvider} in order to create IDEA's peer changelist
 * in response to finding not registered VCS native list
 * it can NOT be done through {@link ChangeListManager} interface; it is for external/IDEA user modifications
 */
public interface ChangeListManagerGate {
  @NotNull
  List<LocalChangeList> getListsCopy();
  @Nullable
  LocalChangeList findChangeList(@Nullable String name);
  @NotNull
  LocalChangeList addChangeList(@NotNull String name, @Nullable String comment);
  @NotNull
  LocalChangeList findOrCreateList(@NotNull String name, @Nullable String comment);

  void editComment(@NotNull String name, @Nullable String comment);
  void editName(@NotNull String oldName, @NotNull String newName);

  void setListsToDisappear(@NotNull Collection<String> names);
  @Nullable
  FileStatus getStatus(@NotNull VirtualFile file);

  @Nullable
  FileStatus getStatus(@NotNull FilePath filePath);

  /**
   * Use {@link #getStatus(FilePath)
   * @deprecated to remove in IDEA 16
   */
  @Deprecated
  FileStatus getStatus(@NotNull File file);

  void setDefaultChangeList(@NotNull String list);
}
