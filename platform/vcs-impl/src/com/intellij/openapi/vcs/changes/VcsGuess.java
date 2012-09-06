/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsGuess {
  private final Project myProject;
  private final ProjectLevelVcsManagerImpl myVcsManager;
  private final FileIndexFacade myExcludedFileIndex;

  VcsGuess(final Project project) {
    myProject = project;
    myVcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManagerImpl.getInstance(myProject);
    myExcludedFileIndex = PeriodicalTasksCloser.getInstance().safeGetService(myProject, FileIndexFacade.class);
  }

  @Nullable
  public AbstractVcs getVcsForDirty(@NotNull final VirtualFile file) {
    if (!file.isInLocalFileSystem()) {
      return null;
    }
    if (isFileInIndex(null, file)) {
      return myVcsManager.getVcsFor(file);
    }
    return null;
  }

  @Nullable
  public AbstractVcs getVcsForDirty(@NotNull final FilePath filePath) {
    if (filePath.isNonLocal()) {
      return null;
    }
    final VirtualFile validParent = ChangesUtil.findValidParentAccurately(filePath);
    if (validParent == null) {
      return null;
    }
    final boolean take = isFileInIndex(filePath, validParent);
    if (take) {
      return myVcsManager.getVcsFor(validParent);
    }
    return null;
  }

  private Boolean isFileInIndex(@Nullable final FilePath filePath, final VirtualFile validParent) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        if (myProject.isDisposed()) return false;
        final boolean inContent = myVcsManager.isFileInContent(validParent);
        if (inContent) return true;
        if (filePath != null) {
          return isFileInBaseDir(filePath, myProject.getBaseDir()) && ! myExcludedFileIndex.isExcludedFile(validParent);
        }
        return false;
      }
    });
  }

  private boolean isFileInBaseDir(final FilePath filePath, final VirtualFile baseDir) {
    final VirtualFile parent = filePath.getVirtualFileParent();
    return !filePath.isDirectory() && parent != null && parent.equals(baseDir);
  }
}
