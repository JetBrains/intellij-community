/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction.showDiffForChange;

public class ShowDiffWithLocalAction extends AnAction implements DumbAware, AnActionExtensionProvider {
  private final boolean myUseBeforeVersion;

  public ShowDiffWithLocalAction() {
    this(false);
    getTemplatePresentation().setIcon(AllIcons.Actions.DiffWithCurrent);
  }

  public ShowDiffWithLocalAction(boolean useBeforeVersion) {
    myUseBeforeVersion = useBeforeVersion;
    ActionUtil.copyFrom(this, useBeforeVersion ? "Vcs.ShowDiffWithLocal.Before" : "Vcs.ShowDiffWithLocal");
  }

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsDataKeys.CHANGES_SELECTION) != null;
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;
    ListSelection<Change> selection = e.getRequiredData(VcsDataKeys.CHANGES_SELECTION);

    ListSelection<Change> changesToLocal = selection.map(change -> {
      return getChangeWithLocal(change);
    });

    if (!changesToLocal.isEmpty()) {
      showDiffForChange(project, changesToLocal);
    }
  }

  public void update(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    ListSelection<Change> selection = e.getData(VcsDataKeys.CHANGES_SELECTION);
    boolean isInAir = CommittedChangesBrowserUseCase.IN_AIR.equals(CommittedChangesBrowserUseCase.DATA_KEY.getData(e.getDataContext()));
    boolean isToolbar = "ChangesBrowser".equals(e.getPlace());

    e.getPresentation().setEnabled(project != null && !isToolbar && selection != null && !isInAir && canShowDiff(selection.getList()));
    e.getPresentation().setVisible(!isToolbar);
  }

  @Nullable
  private Change getChangeWithLocal(@NotNull Change c) {
    ContentRevision revision = myUseBeforeVersion ? c.getBeforeRevision() : c.getAfterRevision();
    if (!isValidRevision(revision)) return null;

    ContentRevision contentRevision = CurrentContentRevision.create(revision.getFile());
    return new Change(revision, contentRevision);
  }

  private boolean canShowDiff(@NotNull List<Change> changes) {
    return ContainerUtil.exists(changes, c -> getChangeWithLocal(c) != null);
  }

  private static boolean isValidRevision(@Nullable ContentRevision revision) {
    return revision != null && !revision.getFile().isNonLocal() && !revision.getFile().isDirectory();
  }

  public static class ShowDiffBeforeWithLocalAction extends ShowDiffWithLocalAction {
    public ShowDiffBeforeWithLocalAction() {
      super(true);
    }
  }
}
