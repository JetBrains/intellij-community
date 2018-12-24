// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.vcs.FilePath;
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
    getTemplatePresentation().setIcon(AllIcons.Actions.Diff);
  }

  public ShowDiffWithLocalAction(boolean useBeforeVersion) {
    myUseBeforeVersion = useBeforeVersion;
    ActionUtil.copyFrom(this, useBeforeVersion ? "Vcs.ShowDiffWithLocal.Before" : "Vcs.ShowDiffWithLocal");
  }

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsDataKeys.CHANGES_SELECTION) != null;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;
    ListSelection<Change> selection = e.getRequiredData(VcsDataKeys.CHANGES_SELECTION);

    ListSelection<Change> changesToLocal = selection.map(change -> getChangeWithLocal(change));

    if (!changesToLocal.isEmpty()) {
      showDiffForChange(project, changesToLocal);
    }
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    ListSelection<Change> selection = e.getData(VcsDataKeys.CHANGES_SELECTION);
    boolean isInAir = CommittedChangesBrowserUseCase.IN_AIR.equals(CommittedChangesBrowserUseCase.DATA_KEY.getData(e.getDataContext()));

    e.getPresentation().setEnabled(project != null && selection != null && !isInAir && canShowDiff(selection.getList()));
  }

  @Nullable
  private Change getChangeWithLocal(@NotNull Change c) {
    ContentRevision revision = myUseBeforeVersion ? c.getBeforeRevision() : c.getAfterRevision();
    ContentRevision otherRevision = myUseBeforeVersion ? c.getAfterRevision() : c.getBeforeRevision();
    if (!isValidRevision(revision)) return null;

    FilePath filePath = revision.getFile();
    if (filePath.getVirtualFile() == null && otherRevision != null) {
      FilePath otherFile = otherRevision.getFile();
      if (otherFile.getVirtualFile() != null) {
        filePath = otherFile;
      }
    }

    ContentRevision contentRevision = CurrentContentRevision.create(filePath);
    return new Change(revision, contentRevision);
  }

  private boolean canShowDiff(@NotNull List<? extends Change> changes) {
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
