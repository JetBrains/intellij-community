// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  LocalChangeList findChangeList(@Nullable @NlsSafe String name);

  @NotNull
  LocalChangeList addChangeList(@NotNull @NlsSafe String name, @Nullable @NlsSafe String comment);

  @NotNull
  LocalChangeList findOrCreateList(@NotNull @NlsSafe String name, @Nullable @NlsSafe String comment);

  void editComment(@NotNull @NlsSafe String name, @Nullable @NlsSafe String comment);

  void editName(@NotNull @NlsSafe String oldName, @NotNull @NlsSafe String newName);

  void setListsToDisappear(@NotNull Collection<@NlsSafe String> names);

  @Nullable
  FileStatus getStatus(@NotNull VirtualFile file);

  @Nullable
  FileStatus getStatus(@NotNull FilePath filePath);

  void setDefaultChangeList(@NotNull String list);
}
