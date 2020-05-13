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
import com.intellij.openapi.vcs.actions.VcsContext;
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
public class CachedCvsContext implements CvsContext{
  private final boolean myIsActive;
  private final Collection<String> myDeletedFileNames;
  private final CvsLightweightFile[] myCvsLightweightFiles;

  private final VcsContext myVcsContext;

  public CachedCvsContext(CvsContext baseContext){
    myIsActive = baseContext.cvsIsActive();
    myDeletedFileNames = baseContext.getDeletedFileNames();
    myCvsLightweightFiles = baseContext.getSelectedLightweightFiles();
    myVcsContext = baseContext;
  }

  @Override
  public Project getProject() {
    return myVcsContext.getProject();
  }

  @Override
  public VirtualFile getSelectedFile() {
    return myVcsContext.getSelectedFile();
  }

  @Override
  public String getActionName() {
    return myVcsContext.getActionName();
  }

  @Override
  public VirtualFile @NotNull [] getSelectedFiles() {
    return myVcsContext.getSelectedFiles();
  }

  @Override
  public Editor getEditor() {
    return myVcsContext.getEditor();
  }

  @Override
  public Collection<VirtualFile> getSelectedFilesCollection() {
    return myVcsContext.getSelectedFilesCollection();
  }

  @Override
  public File[] getSelectedIOFiles() {
    return myVcsContext.getSelectedIOFiles();
  }

  @Override
  public int getModifiers() {
    return myVcsContext.getModifiers();
  }

  @Override
  public Refreshable getRefreshableDialog() {
    return myVcsContext.getRefreshableDialog();
  }

  @Override
  public String getPlace() {
    return myVcsContext.getPlace();
  }

  @Override
  public File getSelectedIOFile() {
    return myVcsContext.getSelectedIOFile();
  }

  @Override
  public boolean cvsIsActive() {
    return myIsActive;
  }

  @Override
  public Collection<String> getDeletedFileNames() {
    return myDeletedFileNames;
  }

  @Override
  public CvsLightweightFile[] getSelectedLightweightFiles() {
    return myCvsLightweightFiles;
  }

  @Override
  public FilePath @NotNull [] getSelectedFilePaths() {
    return myVcsContext.getSelectedFilePaths();
  }

  @Override
  public FilePath getSelectedFilePath() {
    return myVcsContext.getSelectedFilePath();
  }

  @Override
  public ChangeList @Nullable [] getSelectedChangeLists() {
    return myVcsContext.getSelectedChangeLists();
  }

  @Override
  public Change @Nullable [] getSelectedChanges() {
    return myVcsContext.getSelectedChanges();
  }
}
