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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * author: lesya
 */
public class CvsContextAdapter implements CvsContext{
  public Project getProject() {
    return null;
  }

  public boolean cvsIsActive() {
    return false;
  }

  @Nullable
  public VirtualFile getSelectedFile() {
    return null;
  }

  public VirtualFile[] getSelectedFiles() {
    return VirtualFile.EMPTY_ARRAY;
  }

  public Refreshable getRefreshableDialog() {
    return null;
  }

  public Collection<String> getDeletedFileNames() {
    return null;
  }

  @Override
  public String getActionName() {
    return null;
  }

  public Editor getEditor() {
    return null;
  }

  public Collection<VirtualFile> getSelectedFilesCollection() {
    return null;
  }

  public File[] getSelectedIOFiles() {
    return new File[0];
  }

  public int getModifiers() {
    return 0;
  }

  public CvsLightweightFile[] getSelectedLightweightFiles() {
    return new CvsLightweightFile[0];
  }

  public String getPlace() {
    return null;
  }

  public File getSelectedIOFile() {
    return null;
  }

  public FilePath[] getSelectedFilePaths() {
    return null;
  }

  public FilePath getSelectedFilePath() {
    return null;
  }

  @Nullable
  public ChangeList[] getSelectedChangeLists() {
    return null;
  }

  @Nullable
  public Change[] getSelectedChanges() {
    return null;
  }
}
