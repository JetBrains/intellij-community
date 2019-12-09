// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction.showDiffForChange;

// via openapi.vcs.history.actions.ShowDiffBeforeWithLocalAction.ExtensionProvider
// Extending AnAction is left for compatibility with plugins that instantiate this class directly (instead of using ActionManager).
public class ShowDiffWithLocalAction extends AnAction implements DumbAware, AnActionExtensionProvider {
  private final boolean myUseBeforeVersion;

  @SuppressWarnings("unused")
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
    boolean isInAir = CommittedChangesBrowserUseCase.IN_AIR.equals(e.getData(CommittedChangesBrowserUseCase.DATA_KEY));

    e.getPresentation().setEnabled(project != null && selection != null && !isInAir && canShowDiff(selection.getList()));
  }

  @Nullable
  private Change getChangeWithLocal(@NotNull Change c) {
    ContentRevision revision = myUseBeforeVersion ? c.getBeforeRevision() : c.getAfterRevision();
    ContentRevision otherRevision = myUseBeforeVersion ? c.getAfterRevision() : c.getBeforeRevision();

    VirtualFile file = getLocalVirtualFileFor(revision);
    if (file == null) file = getLocalVirtualFileFor(otherRevision); // handle renames gracefully

    ContentRevision localRevision = file != null ? CurrentContentRevision.create(VcsUtil.getFilePath(file)) : null;
    if (revision == null && localRevision == null) return null;

    return new Change(revision, localRevision);
  }

  private boolean canShowDiff(@NotNull List<? extends Change> changes) {
    return ContainerUtil.exists(changes, c -> getChangeWithLocal(c) != null);
  }

  @Nullable
  private static VirtualFile getLocalVirtualFileFor(@Nullable ContentRevision revision) {
    if (revision == null) return null;
    FilePath filePath = revision.getFile();
    if (filePath.isNonLocal() || filePath.isDirectory()) return null;
    return filePath.getVirtualFile();
  }

  @SuppressWarnings("ComponentNotRegistered") // via openapi.vcs.history.actions.ShowDiffBeforeWithLocalAction.ExtensionProvider
  public static class ShowDiffBeforeWithLocalAction extends ShowDiffWithLocalAction {
    public ShowDiffBeforeWithLocalAction() {
      super(true);
    }
  }
}
