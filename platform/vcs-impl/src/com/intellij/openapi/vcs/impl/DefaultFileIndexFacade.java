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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectBaseDirectory;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class DefaultFileIndexFacade extends FileIndexFacade {
  private final Project myProject;
  private final VirtualFile myBaseDir;

  public DefaultFileIndexFacade(@NotNull final Project project) {
    super(project);
    myProject = project;
    myBaseDir = project.getBaseDir();
  }

  public boolean isInContent(@NotNull final VirtualFile file) {
    return VfsUtil.isAncestor(getBaseDir(), file, false);
  }

  @Override
  public boolean isInSource(@NotNull VirtualFile file) {
    return isInContent(file);
  }

  @Override
  public boolean isInSourceContent(@NotNull VirtualFile file) {
    return isInContent(file);
  }

  @Override
  public boolean isInLibraryClasses(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public boolean isInLibrarySource(@NotNull VirtualFile file) {
    return false;
  }

  public boolean isExcludedFile(@NotNull final VirtualFile file) {
    return false;
  }

  @Override
  public Module getModuleForFile(@NotNull VirtualFile file) {
    return null;
  }

  public boolean isValidAncestor(@NotNull final VirtualFile baseDir, @NotNull final VirtualFile childDir) {
    return VfsUtil.isAncestor(baseDir, childDir, false);
  }

  @NotNull
  @Override
  public ModificationTracker getRootModificationTracker() {
    return ModificationTracker.NEVER_CHANGED;
  }

  private VirtualFile getBaseDir() {
    return ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir);
  }
}
