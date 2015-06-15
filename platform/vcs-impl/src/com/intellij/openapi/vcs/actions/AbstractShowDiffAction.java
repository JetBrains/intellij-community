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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.impl.BackgroundableActionEnabledHandler;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractShowDiffAction extends AbstractVcsAction{

  @Override
  protected void update(VcsContext vcsContext, Presentation presentation) {
    updateDiffAction(presentation, vcsContext, getKey());
  }

  protected static void updateDiffAction(final Presentation presentation, final VcsContext vcsContext,
                                         final VcsBackgroundableActions actionKey) {
    presentation.setEnabled(isEnabled(vcsContext, actionKey) != null);
    presentation.setVisible(isVisible(vcsContext));
  }

  @Override
  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }

  protected abstract VcsBackgroundableActions getKey();

  protected static boolean isVisible(final VcsContext vcsContext) {
    final Project project = vcsContext.getProject();
    if (project == null) return false;
    final AbstractVcs[] vcss = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss();
    for (AbstractVcs vcs : vcss) {
      if (vcs.getDiffProvider() != null) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  protected static AbstractVcs isEnabled(final VcsContext vcsContext, @Nullable final VcsBackgroundableActions actionKey) {
    if (!(isVisible(vcsContext))) return null;

    final Project project = vcsContext.getProject();
    if (project == null) return null;
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);

    final VirtualFile[] selectedFilePaths = vcsContext.getSelectedFiles();
    if (selectedFilePaths.length != 1) return null;

    final VirtualFile selectedFile = selectedFilePaths[0];
    if (selectedFile.isDirectory()) return null;

    if (actionKey != null) {
      final BackgroundableActionEnabledHandler handler = ((ProjectLevelVcsManagerImpl)vcsManager).getBackgroundableActionHandler(actionKey);
      if (handler.isInProgress(VcsBackgroundableActions.keyFrom(selectedFile))) return null;
    }

    final AbstractVcs vcs = vcsManager.getVcsFor(selectedFile);
    if (vcs == null) return null;

    final DiffProvider diffProvider = vcs.getDiffProvider();

    if (diffProvider == null) return null;

    if (AbstractVcs.fileInVcsByFileStatus(project, VcsUtil.getFilePath(selectedFile))) {
      return vcs;
    }
    return null;
  }


  @Override
  protected void actionPerformed(@NotNull VcsContext vcsContext) {
    final Project project = vcsContext.getProject();
    if (project == null) return;
    if (ChangeListManager.getInstance(project).isFreezedWithNotification("Can not " + vcsContext.getActionName() + " now")) return;
    final VirtualFile selectedFile = vcsContext.getSelectedFiles()[0];

    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    final AbstractVcs vcs = vcsManager.getVcsFor(selectedFile);
    final DiffProvider diffProvider = vcs.getDiffProvider();

    final DiffActionExecutor actionExecutor = getExecutor(diffProvider, selectedFile, project);
    actionExecutor.showDiff();
  }

  protected DiffActionExecutor getExecutor(final DiffProvider diffProvider, final VirtualFile selectedFile, final Project project) {
    return new DiffActionExecutor.CompareToCurrentExecutor(diffProvider, selectedFile, project, getKey());
  }
}
