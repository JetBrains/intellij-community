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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public class VcsGuess {
  private final Project myProject;
  private final ProjectLevelVcsManagerImpl myVcsManager;
  private final ExcludedFileIndex myExcludedFileIndex;

  VcsGuess(final Project project) {
    myProject = project;
    myVcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManagerImpl.getInstance(myProject);
    myExcludedFileIndex = ExcludedFileIndex.getInstance(myProject);
  }

  @Nullable
  public AbstractVcs getVcsForDirty(final VirtualFile file) {
    if (!file.isInLocalFileSystem()) {
      return null;
    }
    if (myExcludedFileIndex.isInContent(file) || isFileInBaseDir(file) ||
        myVcsManager.hasExplicitMapping(file) || file.equals(myProject.getBaseDir())) {
      if (myExcludedFileIndex.isExcludedFile(file)) {
        return null;
      }
      return myVcsManager.getVcsFor(file);
    }
    return null;
  }

  @Nullable
  public AbstractVcs getVcsForDirty(final FilePath filePath) {
    if (filePath.isNonLocal()) {
      return null;
    }
    final VirtualFile validParent = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        return ChangesUtil.findValidParent(filePath);
      }
    });
    if (validParent == null) {
      return null;
    }
    if (myExcludedFileIndex.isInContent(validParent) || isFileInBaseDir(filePath) ||
        myVcsManager.hasExplicitMapping(validParent) || isInDirectoryBasedRoot(validParent)) {
      if (myExcludedFileIndex.isExcludedFile(validParent)) {
        return null;
      }
      return myVcsManager.getVcsFor(validParent);
    }
    return null;
  }

  private boolean isInDirectoryBasedRoot(final VirtualFile file) {
    if (file == null) return false;
    final StorageScheme storageScheme = ((ProjectEx) myProject).getStateStore().getStorageScheme();
    if (StorageScheme.DIRECTORY_BASED.equals(storageScheme)) {
      final VirtualFile baseDir = myProject.getBaseDir();
      if (baseDir == null) return false;
      final VirtualFile ideaDir = baseDir.findChild(Project.DIRECTORY_STORE_FOLDER);
      return (ideaDir != null && ideaDir.isValid() && ideaDir.isDirectory() && VfsUtil.isAncestor(ideaDir, file, false));
    }
    return false;
  }

  private boolean isFileInBaseDir(final VirtualFile file) {
    VirtualFile parent = file.getParent();
    return !file.isDirectory() && parent != null && parent.equals(myProject.getBaseDir());
  }

  private boolean isFileInBaseDir(final FilePath filePath) {
    final VirtualFile parent = filePath.getVirtualFileParent();
    return !filePath.isDirectory() && parent != null && parent.equals(myProject.getBaseDir());
  }
}
