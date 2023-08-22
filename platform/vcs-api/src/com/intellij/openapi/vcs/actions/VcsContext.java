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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PlaceProvider;
import com.intellij.util.containers.ContainerUtil;
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

  @NotNull
  default Stream<VirtualFile> getSelectedFilesStream() {
    return Arrays.stream(getSelectedFiles());
  }

  /**
   * @deprecated use {@link #getSelectedUnversionedFilePaths}
   */
  @Deprecated(forRemoval = true)
  @NotNull
  default List<VirtualFile> getSelectedUnversionedFiles() {
    return ContainerUtil.mapNotNull(getSelectedUnversionedFilePaths(), FilePath::getVirtualFile);
  }

  @NotNull
  default List<FilePath> getSelectedUnversionedFilePaths() {
    return emptyList();
  }

  Editor getEditor();

  Collection<VirtualFile> getSelectedFilesCollection();

  File[] getSelectedIOFiles();

  int getModifiers();

  File getSelectedIOFile();

  FilePath @NotNull [] getSelectedFilePaths();

  @NotNull
  default Stream<FilePath> getSelectedFilePathsStream() {
    return Arrays.stream(getSelectedFilePaths());
  }

  @Nullable
  FilePath getSelectedFilePath();

  ChangeList @Nullable [] getSelectedChangeLists();

  Change @Nullable [] getSelectedChanges();

  @NlsActions.ActionText
  String getActionName();
}
