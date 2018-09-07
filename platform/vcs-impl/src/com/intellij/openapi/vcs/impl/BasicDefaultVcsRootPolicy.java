// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectBaseDirectory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class BasicDefaultVcsRootPolicy extends DefaultVcsRootPolicy {
  private final Project myProject;
  private final VirtualFile myBaseDir;

  public BasicDefaultVcsRootPolicy(Project project) {
    myProject = project;
    myBaseDir = project.getBaseDir();
  }

  @Override
  @NotNull
  public Collection<VirtualFile> getDefaultVcsRoots(@NotNull NewMappings mappingList, @NotNull String vcsName) {
    List<VirtualFile> result = ContainerUtil.newArrayList();
    final VirtualFile baseDir = ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir);
    if (baseDir != null && vcsName.equals(mappingList.getVcsFor(baseDir))) {
      result.add(baseDir);
    }
    return result;
  }

  @Override
  public boolean matchesDefaultMapping(@NotNull final VirtualFile file, final Object matchContext) {
    return VfsUtil.isAncestor(ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir), file, false);
  }

  @Override
  @Nullable
  public Object getMatchContext(final VirtualFile file) {
    return null;
  }

  @Override
  @Nullable
  public VirtualFile getVcsRootFor(@NotNull final VirtualFile file) {
    return ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir);
  }

  @Override
  @NotNull
  public Collection<VirtualFile> getDirtyRoots() {
    return Collections.singletonList(ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir));
  }

}
