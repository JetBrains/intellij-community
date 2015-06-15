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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;


public abstract class AbstractCommonCheckinAction extends AbstractVcsAction {
  
  private static final Logger LOG = Logger.getInstance(AbstractCommonCheckinAction.class);
  
  @Override
  public void actionPerformed(@NotNull final VcsContext context) {
    LOG.debug("actionPerformed. ");
    final Project project = context.getProject();
    if (project == null) {
      LOG.debug("project is null. returning.");
      return;
    }
    if (ChangeListManager.getInstance(project).isFreezedWithNotification("Can not " + getMnemonicsFreeActionName(context) + " now")) {
      LOG.debug("ChangeListManager is freezed. returning.");
      return;
    }

    if (ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()) {
      LOG.debug("Background operation is running. returning.");
      return;
    }

    final FilePath[] roots = prepareRootsForCommit(getRoots(context), project);
    ChangeListManager.getInstance(project).invokeAfterUpdate(new Runnable() {
      @Override
      public void run() {
        performCheckIn(context, project, roots);
      }
    }, InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE, VcsBundle.message("waiting.changelists.update.for.show.commit.dialog.message"),
                                                             ModalityState.current());
  }

  protected void performCheckIn(@NotNull VcsContext context, @NotNull Project project, @NotNull FilePath[] roots) {
    LOG.debug("invoking commit dialog after update");
    LocalChangeList initialSelection = getInitiallySelectedChangeList(context, project);
    Change[] changes = context.getSelectedChanges();
    if (changes != null && changes.length > 0) {
      CommitChangeListDialog.commitChanges(project, Arrays.asList(changes), initialSelection, getExecutor(project), null);
    }
    else {
      CommitChangeListDialog.commitPaths(project, Arrays.asList(roots), initialSelection, getExecutor(project), null);
    }
  }

  @NotNull
  protected FilePath[] prepareRootsForCommit(@NotNull FilePath[] roots, @NotNull Project project) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().saveAll();
    }

    return filterDescindingFiles(roots, project);
  }

  protected String getMnemonicsFreeActionName(VcsContext context) {
    return getActionName(context);
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

  protected abstract String getActionName(@NotNull VcsContext dataContext);

  @NotNull
  protected abstract FilePath[] getRoots(@NotNull VcsContext dataContext);

  protected abstract boolean approximatelyHasRoots(final VcsContext dataContext);

  @Override
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

    /*if (! checkEnvironments(plVcsManager)) {
      presentation.setEnabled(false);
      return;
    }*/

    if (! approximatelyHasRoots(vcsContext)) {
      presentation.setEnabled(false);
      return;
    }

    String actionName = getActionName(vcsContext) + "...";
    presentation.setText(actionName);

    presentation.setEnabled(! plVcsManager.isBackgroundVcsOperationRunning());
    presentation.setVisible(true);
  }

  /*protected static boolean checkEnvironments(ProjectLevelVcsManager plVcsManager) {
    final AbstractVcs[] allActiveVcss = plVcsManager.getAllActiveVcss();
    for (AbstractVcs vcs : allActiveVcss) {
      if (vcs.getCheckinEnvironment() != null) {
        return true;
      }
    }
    return false;
  }*/

  @Override
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
      virtualFiles.add(VcsUtil.getFilePath(root));
    }
    return virtualFiles.toArray(new FilePath[virtualFiles.size()]);
  }
}
