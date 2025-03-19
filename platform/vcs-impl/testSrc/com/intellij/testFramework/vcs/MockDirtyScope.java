/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.testFramework.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.VcsModifiableDirtyScope;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Mock implementation of {@link com.intellij.openapi.vcs.changes.VcsDirtyScope}.
 * Stores files and dirs separately without any check.
 * Not all operations may be supported.
 *
 * @author Kirill Likhodedov
 */
public class MockDirtyScope extends VcsModifiableDirtyScope {

  private final Project myProject;
  private final AbstractVcs myVcs;
  private final ProjectLevelVcsManager myVcsManager;

  private final Set<FilePath> myDirtyFiles = new HashSet<>();
  private final Set<FilePath> myDirtyDirs = new HashSet<>();
  private final Set<VirtualFile> myContentRoots = new HashSet<>();

  public MockDirtyScope(@NotNull Project project, @NotNull AbstractVcs vcs) {
    myProject = project;
    myVcs = vcs;
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
  }

  @Override
  public void addDirtyFile(@Nullable FilePath newcomer) {
    myDirtyFiles.add(newcomer);
    myContentRoots.add(myVcsManager.getVcsRootFor(newcomer.getVirtualFile()));
  }

  @Override
  public void addDirtyDirRecursively(@Nullable FilePath newcomer) {
    myDirtyDirs.add(newcomer);
    myContentRoots.add(myVcsManager.getVcsRootFor(newcomer.getVirtualFile()));
  }

  @Override
  @NotNull
  public Collection<VirtualFile> getAffectedContentRoots() {
    return myContentRoots;
  }

  @Override
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  @NotNull
  public AbstractVcs getVcs() {
    return myVcs;
  }

  @Override
  @NotNull
  public Set<FilePath> getDirtyFiles() {
    return myDirtyFiles;
  }

  @Override
  @NotNull
  public Set<FilePath> getDirtyFilesNoExpand() {
    return myDirtyFiles;
  }

  @Override
  @NotNull
  public Set<FilePath> getRecursivelyDirtyDirectories() {
    return myDirtyDirs;
  }

  @Override
  public boolean isEmpty() {
    return myDirtyFiles.isEmpty();
  }

  @Override
  public boolean belongsTo(@NotNull FilePath path) {
    if (myDirtyFiles.contains(path)) return true;

    for (FilePath parent : myDirtyDirs) {
      if (FileUtil.startsWith(path.getPath(), parent.getPath())) return true;
    }

    return false;
  }

  @Override
  public boolean wasEveryThingDirty() {
    return false;
  }
}
