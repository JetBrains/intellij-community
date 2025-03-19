// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PlaceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;

/**
 * @see VcsContextFactory
 * @see com.intellij.openapi.vcs.actions.VcsContextUtil
 * @deprecated Prefer explicit {@link com.intellij.openapi.actionSystem.DataContext} state caching when needed.
 */
@Deprecated(forRemoval = true)
public interface VcsContext extends PlaceProvider {
  @Nullable Project getProject();

  @Nullable
  VirtualFile getSelectedFile();

  VirtualFile @NotNull [] getSelectedFiles();

  default @NotNull Stream<VirtualFile> getSelectedFilesStream() {
    return Arrays.stream(getSelectedFiles());
  }

  default @NotNull List<FilePath> getSelectedUnversionedFilePaths() {
    return emptyList();
  }

  Editor getEditor();

  Collection<VirtualFile> getSelectedFilesCollection();

  File[] getSelectedIOFiles();

  int getModifiers();

  File getSelectedIOFile();

  FilePath @NotNull [] getSelectedFilePaths();

  default @NotNull Stream<FilePath> getSelectedFilePathsStream() {
    return Arrays.stream(getSelectedFilePaths());
  }

  @Nullable
  FilePath getSelectedFilePath();

  ChangeList @Nullable [] getSelectedChangeLists();

  Change @Nullable [] getSelectedChanges();

  @NlsActions.ActionText
  String getActionName();
}
