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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PlaceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

public interface VcsContext extends PlaceProvider<String> {
  @Nullable Project getProject();

  @Nullable
  VirtualFile getSelectedFile();

  @NotNull
  VirtualFile[] getSelectedFiles();

  @NotNull
  default Stream<VirtualFile> getSelectedFilesStream() {
    return Arrays.stream(getSelectedFiles());
  }

  Editor getEditor();

  Collection<VirtualFile> getSelectedFilesCollection();

  File[] getSelectedIOFiles();

  int getModifiers();

  Refreshable getRefreshableDialog();

  File getSelectedIOFile();

  @NotNull
  FilePath[] getSelectedFilePaths();

  @NotNull
  default Stream<FilePath> getSelectedFilePathsStream() {
    return Arrays.stream(getSelectedFilePaths());
  }

  @Nullable
  FilePath getSelectedFilePath();

  @Nullable
  ChangeList[] getSelectedChangeLists();

  @Nullable
  Change[] getSelectedChanges();

  String getActionName();
}
