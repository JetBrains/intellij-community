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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public abstract class AbstractVcsAction extends DumbAwareAction {
  public static Collection<AbstractVcs> getActiveVcses(VcsContext dataContext) {
    Collection<AbstractVcs> result = new HashSet<>();
    final Project project = dataContext.getProject();
    if (project != null) {
      Collections.addAll(result, ProjectLevelVcsManager.getInstance(project).getAllActiveVcss());
    }
    return result;
  }

  @NotNull
  protected static FilePath[] filterDescindingFiles(@NotNull FilePath[] roots, Project project) {
    return DescindingFilesFilter.filterDescindingFiles(roots, project);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    performUpdate(e.getPresentation(), VcsContextWrapper.createInstanceOn(e));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    actionPerformed(VcsContextWrapper.createCachedInstanceOn(e));
  }

  protected abstract void update(@NotNull VcsContext vcsContext, @NotNull Presentation presentation);

  protected abstract void actionPerformed(@NotNull VcsContext e);

  // Not used actually. Required for compatibility with external plugins.
  protected boolean forceSyncUpdate(@NotNull AnActionEvent e) {
    return true;
  }

  // Required for compatibility with external plugins.
  protected void performUpdate(@NotNull Presentation presentation, @NotNull VcsContext vcsContext) {
    update(vcsContext, presentation);
  }
}
