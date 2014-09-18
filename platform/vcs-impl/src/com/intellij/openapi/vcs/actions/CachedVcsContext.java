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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

public class CachedVcsContext implements VcsContext {
  private final Project myProject;
  private final VirtualFile mySelectedFile;
  private final VirtualFile[] mySelectedFiles;
  private final Collection<VirtualFile> mySelectedFilesCollection;
  private final Editor myEditor;
  private final File[] mySelectedIOFiles;
  private final int myModifiers;
  private final Refreshable myRefreshablePanel;
  private final String myPlace;
  private final File mySelectedIOFile;
  private final FilePath[] mySelectedFilePaths;
  private final FilePath mySelectedFilePath;
  private final ChangeList[] mySelectedChangeLists;
  private final Change[] mySelectedChanges;
  private final String myActionName;

  public CachedVcsContext(VcsContext baseContext) {
    myProject = baseContext.getProject();
    mySelectedFile = baseContext.getSelectedFile();
    mySelectedFiles = baseContext.getSelectedFiles();
    mySelectedFilesCollection = baseContext.getSelectedFilesCollection();
    myEditor = baseContext.getEditor();
    mySelectedIOFiles = baseContext.getSelectedIOFiles();
    myModifiers = baseContext.getModifiers();
    myRefreshablePanel = baseContext.getRefreshableDialog();
    myPlace = baseContext.getPlace();
    mySelectedIOFile = baseContext.getSelectedIOFile();
    mySelectedFilePaths = baseContext.getSelectedFilePaths();
    mySelectedFilePath = baseContext.getSelectedFilePath();
    mySelectedChangeLists = baseContext.getSelectedChangeLists();
    mySelectedChanges = baseContext.getSelectedChanges();
    myActionName = baseContext.getActionName();
  }

  @Override
  public String getPlace() {
    return myPlace;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public VirtualFile getSelectedFile() {
    return mySelectedFile;
  }

  @Override
  @NotNull
  public VirtualFile[] getSelectedFiles() {
    return mySelectedFiles;
  }

  @Override
  public Editor getEditor() {
    return myEditor;
  }

  @Override
  public Collection<VirtualFile> getSelectedFilesCollection() {
    return mySelectedFilesCollection;
  }

  @Override
  public File[] getSelectedIOFiles() {
    return mySelectedIOFiles;
  }

  @Override
  public int getModifiers() {
    return myModifiers;
  }

  @Override
  public Refreshable getRefreshableDialog() {
    return myRefreshablePanel;
  }

  @Override
  public File getSelectedIOFile() {
    return mySelectedIOFile;
  }

  @Override
  @NotNull
  public FilePath[] getSelectedFilePaths() {
    return mySelectedFilePaths;
  }

  @Override
  public FilePath getSelectedFilePath() {
    return mySelectedFilePath;
  }

  @Override
  @Nullable
  public ChangeList[] getSelectedChangeLists() {
    return mySelectedChangeLists;
  }

  @Override
  @Nullable
  public Change[] getSelectedChanges() {
    return mySelectedChanges;
  }

  @Override
  public String getActionName() {
    return myActionName;
  }
}
