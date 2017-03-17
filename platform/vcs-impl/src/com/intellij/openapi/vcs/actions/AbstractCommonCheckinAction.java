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

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;
import com.intellij.util.ObjectUtils;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

import static com.intellij.util.ArrayUtil.isEmpty;
import static com.intellij.util.containers.UtilKt.stream;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toSet;

public abstract class AbstractCommonCheckinAction extends AbstractVcsAction {
  
  private static final Logger LOG = Logger.getInstance(AbstractCommonCheckinAction.class);
  
  @Override
  public void actionPerformed(@NotNull VcsContext context) {
    LOG.debug("actionPerformed. ");
    Project project = ObjectUtils.notNull(context.getProject());

    if (ChangeListManager.getInstance(project).isFreezedWithNotification("Can not " + getMnemonicsFreeActionName(context) + " now")) {
      LOG.debug("ChangeListManager is freezed. returning.");
    }
    else if (ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()) {
      LOG.debug("Background operation is running. returning.");
    }
    else {
      FilePath[] roots = prepareRootsForCommit(getRoots(context), project);
      ChangeListManager.getInstance(project)
        .invokeAfterUpdate(() -> performCheckIn(context, project, roots), InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE,
                           VcsBundle.message("waiting.changelists.update.for.show.commit.dialog.message"), ModalityState.current());
    }
  }

  protected void performCheckIn(@NotNull VcsContext context, @NotNull Project project, @NotNull FilePath[] roots) {
    LOG.debug("invoking commit dialog after update");
    LocalChangeList initialSelection = getInitiallySelectedChangeList(context, project);
    Change[] selectedChanges = context.getSelectedChanges();
    Collection<Change> changesToCommit = !isEmpty(selectedChanges) ? asList(selectedChanges) : getChangesIn(project, roots);

    CommitChangeListDialog.commitChanges(project, changesToCommit, initialSelection, getExecutor(project), null);
  }

  @NotNull
  private static Set<Change> getChangesIn(@NotNull Project project, @NotNull FilePath[] roots) {
    ChangeListManager manager = ChangeListManager.getInstance(project);
    return stream(roots)
      .flatMap(path -> manager.getChangesIn(path).stream())
      .collect(toSet());
  }

  @NotNull
  protected FilePath[] prepareRootsForCommit(@NotNull FilePath[] roots, @NotNull Project project) {
    ApplicationManager.getApplication().saveAll();

    return DescindingFilesFilter.filterDescindingFiles(roots, project);
  }

  protected String getMnemonicsFreeActionName(@NotNull VcsContext context) {
    return getActionName(context);
  }

  @Nullable
  protected CommitExecutor getExecutor(@NotNull Project project) {
    return null;
  }

  @Nullable
  protected LocalChangeList getInitiallySelectedChangeList(@NotNull VcsContext context, @NotNull Project project) {
    LocalChangeList result;
    ChangeListManager manager = ChangeListManager.getInstance(project);
    ChangeList[] changeLists = context.getSelectedChangeLists();

    if (!isEmpty(changeLists)) {
      // convert copy to real
      result = manager.findChangeList(changeLists[0].getName());
    }
    else {
      Change[] changes = context.getSelectedChanges();
      result = !isEmpty(changes) ? manager.getChangeList(changes[0]) : manager.getDefaultChangeList();
    }

    return result;
  }

  protected abstract String getActionName(@NotNull VcsContext dataContext);

  @NotNull
  protected abstract FilePath[] getRoots(@NotNull VcsContext dataContext);

  protected abstract boolean approximatelyHasRoots(@NotNull VcsContext dataContext);

  @Override
  protected void update(@NotNull VcsContext vcsContext, @NotNull Presentation presentation) {
    Project project = vcsContext.getProject();

    if (project == null || !ProjectLevelVcsManager.getInstance(project).hasActiveVcss()) {
      presentation.setEnabledAndVisible(false);
    }
    else if (!approximatelyHasRoots(vcsContext)) {
      presentation.setEnabled(false);
    }
    else {
      presentation.setText(getActionName(vcsContext) + "...");
      presentation.setEnabled(!ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning());
      presentation.setVisible(true);
    }
  }

  @NotNull
  protected static FilePath[] getAllContentRoots(@NotNull VcsContext context) {
    return Stream.of(ProjectLevelVcsManager.getInstance(context.getProject()).getAllVersionedRoots())
      .map(VcsUtil::getFilePath)
      .toArray(FilePath[]::new);
  }
}
