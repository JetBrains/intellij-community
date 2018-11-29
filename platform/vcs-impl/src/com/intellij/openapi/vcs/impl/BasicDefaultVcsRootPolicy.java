// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectBaseDirectory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class BasicDefaultVcsRootPolicy extends DefaultVcsRootPolicy {
  private final Project myProject;
  private final VirtualFile myBaseDir;

  public BasicDefaultVcsRootPolicy(Project project) {
    myProject = project;
    myBaseDir = project.getBaseDir();
  }

  @Override
  @NotNull
  public Collection<VirtualFile> getDefaultVcsRoots() {
    List<VirtualFile> result = ContainerUtil.newArrayList();
    final VirtualFile baseDir = ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir);
    if (baseDir != null) result.add(baseDir);
    return result;
  }

}
