// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static java.util.Collections.emptySet;

/**
 * @deprecated Use {@link DumbAwareAction} instead.
 */
@ApiStatus.Internal
@Deprecated(forRemoval = true)
public abstract class AbstractVcsAction extends DumbAwareAction {

  @SuppressWarnings("unused") // Required for compatibility with external plugins.
  public static Collection<AbstractVcs> getActiveVcses(@NotNull VcsContext dataContext) {
    Project project = dataContext.getProject();

    return project != null ? ContainerUtil.newHashSet(ProjectLevelVcsManager.getInstance(project).getAllActiveVcss()) : emptySet();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    update(VcsContextWrapper.createInstanceOn(e), e.getPresentation());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    actionPerformed(VcsContextWrapper.createCachedInstanceOn(e));
  }

  protected abstract void update(@NotNull VcsContext vcsContext, @NotNull Presentation presentation);

  protected abstract void actionPerformed(@NotNull VcsContext e);
}
