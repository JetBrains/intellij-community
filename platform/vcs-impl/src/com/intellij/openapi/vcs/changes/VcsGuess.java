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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsGuess {

  @NotNull private final Project myProject;
  @NotNull private final ProjectLevelVcsManagerImpl myVcsManager;

  public VcsGuess(@NotNull Project project) {
    myProject = project;
    myVcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
  }

  @Nullable
  public AbstractVcs getVcsForDirty(@NotNull VirtualFile file) {
    if (file.isInLocalFileSystem() && isFileInIndex(file)) {
      return myVcsManager.getVcsFor(file);
    }
    return null;
  }

  @Nullable
  public AbstractVcs getVcsForDirty(@NotNull FilePath filePath) {
    if (filePath.isNonLocal()) {
      return null;
    }
    VirtualFile validParent = ChangesUtil.findValidParentAccurately(filePath);
    if (validParent != null && isFileInIndex(validParent)) {
      return myVcsManager.getVcsFor(validParent);
    }
    return null;
  }

  private boolean isFileInIndex(@NotNull VirtualFile validParent) {
    return ReadAction.compute(() -> {
      if (myProject.isDisposed()) return false;
      return myVcsManager.isFileInContent(validParent);
    });
  }
}
