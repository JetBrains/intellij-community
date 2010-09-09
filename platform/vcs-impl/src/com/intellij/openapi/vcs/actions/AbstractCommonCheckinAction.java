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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;


public abstract class AbstractCommonCheckinAction extends AbstractVcsAction {
  public void actionPerformed(final VcsContext context) {
    final Project project = context.getProject();

    if (project == null) return;

    if (ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()) {
      return;
    }

    final FilePath[] roots = filterDescindingFiles(getRoots(context), project);

    if (ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().saveAll();
    }

    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    changeListManager.invokeAfterUpdate(new Runnable() {
      public void run() {

        final LocalChangeList initialSelection = getInitiallySelectedChangeList(context, project);

        Change[] changes = context.getSelectedChanges();
        if (changes != null && changes.length > 0) {
          Collection<Change> changeCollection = new ArrayList<Change>();
          Collections.addAll(changeCollection, changes);
          CommitChangeListDialog.commitChanges(project, changeCollection, initialSelection, getExecutor(project), null);
        }
        else {
          CommitChangeListDialog.commitPaths(project, Arrays.asList(roots), initialSelection, getExecutor(project), null);
        }
      }
    }, InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE, VcsBundle.message("waiting.changelists.update.for.show.commit.dialog.message"), ModalityState.current());
  }

  @Nullable
  protected CommitExecutor getExecutor(Project project) {
    return null;
  }

  @Nullable
  protected LocalChangeList getInitiallySelectedChangeList(final VcsContext context, final Project project) {
    LocalChangeList initialSelection;
    ChangeList[] selectedChangeLists = context.getSelectedChangeLists();
    if (selectedChangeLists != null && selectedChangeLists.length > 0) {
      // convert copy to real
      initialSelection = ChangeListManager.getInstance(project).findChangeList(selectedChangeLists [0].getName());
    }
    else {
      Change[] selectedChanges = context.getSelectedChanges();
      if (selectedChanges != null && selectedChanges.length > 0) {
        initialSelection = ChangeListManager.getInstance(project).getChangeList(selectedChanges [0]);
      }
      else {
        initialSelection = ChangeListManager.getInstance(project).getDefaultChangeList();
      }
    }
    return initialSelection;
  }

  @Nullable
  protected static AbstractVcs getCommonVcsFor(FilePath[] roots, Project project) {
    if (roots.length == 0) return null;
    AbstractVcs firstVcs = VcsUtil.getVcsFor(project, roots[0]);
    if (firstVcs == null) return null;

    for (FilePath file : roots) {
      AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
      if (vcs == null) return null;
      if (firstVcs != vcs) {
        return null;
      }
    }
    return firstVcs;
  }

  protected abstract String getActionName(VcsContext dataContext);

  protected abstract FilePath[] getRoots(VcsContext dataContext);

  protected abstract boolean approximatelyHasRoots(final VcsContext dataContext);

  protected void update(VcsContext vcsContext, Presentation presentation) {
    Project project = vcsContext.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
    if (! plVcsManager.hasActiveVcss()) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    if (! approximatelyHasRoots(vcsContext)) {
      presentation.setEnabled(false);
      return;
    }

    String actionName = getActionName(vcsContext) + "...";
    presentation.setText(actionName);

    presentation.setEnabled(! plVcsManager.isBackgroundVcsOperationRunning());
    presentation.setVisible(true);
  }

  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }

  protected abstract boolean filterRootsBeforeAction();

  protected static FilePath[] getAllContentRoots(final VcsContext context) {
    Project project = context.getProject();
    ArrayList<FilePath> virtualFiles = new ArrayList<FilePath>();
    ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
    VirtualFile[] roots = manager.getAllVersionedRoots();
    for (VirtualFile root : roots) {
      virtualFiles.add(new FilePathImpl(root));
    }
    return virtualFiles.toArray(new FilePath[virtualFiles.size()]);
  }
}
