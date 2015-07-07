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
package com.intellij.openapi.vcs.impl;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.DirtBuilder;
import com.intellij.openapi.vcs.changes.VcsGuess;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public abstract class DefaultVcsRootPolicy {
  public static DefaultVcsRootPolicy getInstance(Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetService(project, DefaultVcsRootPolicy.class);
  }

  public abstract void addDefaultVcsRoots(final NewMappings mappingList, @NotNull String vcsName, List<VirtualFile> result);

  public abstract boolean matchesDefaultMapping(final VirtualFile file, final Object matchContext);

  @Nullable
  public abstract Object getMatchContext(final VirtualFile file);

  @Nullable
  public abstract VirtualFile getVcsRootFor(final VirtualFile file);

  @NotNull
  public abstract Collection<VirtualFile> getDirtyRoots();
  
  public String getProjectConfigurationMessage(final Project project) {
    boolean isDirectoryBased = ProjectUtil.isDirectoryBased(project);
    final String[] parts = new String[] {"Content roots of all modules", "all immediate descendants of project base directory",
      Project.DIRECTORY_STORE_FOLDER + " directory contents"};
    final StringBuilder sb = new StringBuilder(parts[0]);
    if (isDirectoryBased) {
      sb.append(", ");
    } else {
      sb.append(", and ");
    }
    sb.append(parts[1]);
    if (isDirectoryBased) {
      sb.append(", and ");
      sb.append(parts[2]);
    }
    return sb.toString();
  }
}
