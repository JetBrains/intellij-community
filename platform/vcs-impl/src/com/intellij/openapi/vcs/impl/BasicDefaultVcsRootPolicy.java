// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectBaseDirectory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BasicDefaultVcsRootPolicy extends DefaultVcsRootPolicy {
  public BasicDefaultVcsRootPolicy(@NotNull Project project) {
    super(project);
  }

  @Override
  @NotNull
  public Collection<VirtualFile> getDefaultVcsRoots() {
    List<VirtualFile> result = new ArrayList<>();
    final VirtualFile baseDir = ProjectBaseDirectory.getInstance(myProject).getBaseDir(myProject.getBaseDir());
    if (baseDir != null) result.add(baseDir);
    return result;
  }
}
