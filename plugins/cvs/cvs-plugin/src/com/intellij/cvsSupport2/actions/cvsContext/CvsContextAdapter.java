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
package com.intellij.cvsSupport2.actions.cvsContext;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * author: lesya
 */
public class CvsContextAdapter implements CvsContext{
  @Override
  public Project getProject() {
    return null;
  }

  @Override
  public boolean cvsIsActive() {
    return false;
  }

  @Override
  @Nullable
  public VirtualFile getSelectedFile() {
    return null;
  }

  @Override
  public VirtualFile @NotNull [] getSelectedFiles() {
    return VirtualFile.EMPTY_ARRAY;
  }

  @Override
  public Refreshable getRefreshableDialog() {
    return null;
  }

  @Override
  public Collection<String> getDeletedFileNames() {
    return null;
  }

  @Override
  public String getActionName() {
    return null;
  }

  @Override
  public Editor getEditor() {
    return null;
  }

  @Override
  public Collection<VirtualFile> getSelectedFilesCollection() {
    return null;
  }

  @Override
  public File[] getSelectedIOFiles() {
    return new File[0];
  }

  @Override
  public int getModifiers() {
    return 0;
  }

  @Override
  public CvsLightweightFile[] getSelectedLightweightFiles() {
    return new CvsLightweightFile[0];
  }

  @Override
  public String getPlace() {
    return null;
  }

  @Override
  public File getSelectedIOFile() {
    return null;
  }

  @Override
  public FilePath @NotNull [] getSelectedFilePaths() {
    return new FilePath[] {};
  }

  @Override
  public FilePath getSelectedFilePath() {
    return null;
  }

  @Override
  public ChangeList @Nullable [] getSelectedChangeLists() {
    return null;
  }

  @Override
  public Change @Nullable [] getSelectedChanges() {
    return null;
  }
}
