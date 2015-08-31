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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ProjectBaseDirectory;
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

  public void addDefaultVcsRoots(final NewMappings mappingList, @NotNull final String vcsName, final List<VirtualFile> result) {
    final VirtualFile baseDir = ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir);
    if (baseDir != null && vcsName.equals(mappingList.getVcsFor(baseDir))) {
      result.add(baseDir);
    }
  }

  public boolean matchesDefaultMapping(final VirtualFile file, final Object matchContext) {
    return VfsUtil.isAncestor(ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir), file, false);
  }

  @Nullable
  public Object getMatchContext(final VirtualFile file) {
    return null;
  }

  @Nullable
  public VirtualFile getVcsRootFor(final VirtualFile file) {
    return ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir);
  }

  @NotNull
  public Collection<VirtualFile> getDirtyRoots() {
    return Collections.singletonList(ProjectBaseDirectory.getInstance(myProject).getBaseDir(myBaseDir));
  }

}
