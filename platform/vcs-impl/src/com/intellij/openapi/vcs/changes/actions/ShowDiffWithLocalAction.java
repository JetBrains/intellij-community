// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsActions;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    ActionUtil.copyFrom(this, useBeforeVersion ? VcsActions.DIFF_BEFORE_WITH_LOCAL : VcsActions.DIFF_AFTER_WITH_LOCAL);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsDataKeys.CHANGES_SELECTION) != null;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;
    ListSelection<Change> selection = e.getRequiredData(VcsDataKeys.CHANGES_SELECTION);

    DiffRequestChain chain = new WithLocalRequestChain(project, selection, myUseBeforeVersion);
    DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT);
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    ListSelection<Change> selection = e.getData(VcsDataKeys.CHANGES_SELECTION);
    boolean isInAir = CommittedChangesBrowserUseCase.IN_AIR.equals(e.getData(CommittedChangesBrowserUseCase.DATA_KEY));

    e.getPresentation().setEnabled(project != null && selection != null && !isInAir && canShowDiff(selection.getList()));
  }

  private boolean canShowDiff(@NotNull List<? extends Change> changes) {
    return ContainerUtil.exists(changes, c -> getChangeWithLocal(c, myUseBeforeVersion) != null);
  }

  public static @Nullable Change getChangeWithLocal(@NotNull Change c, boolean useBeforeVersion) {
    return getChangeWithLocal(c, useBeforeVersion, true);
  }

  public static @Nullable Change getChangeWithLocal(@NotNull Change c, boolean useBeforeVersion, boolean isAfterRevisionLocal) {
    ContentRevision revision = useBeforeVersion ? c.getBeforeRevision() : c.getAfterRevision();
    ContentRevision otherRevision = useBeforeVersion ? c.getAfterRevision() : c.getBeforeRevision();

    VirtualFile file = getLocalVirtualFileFor(revision);
    if (file == null) file = getLocalVirtualFileFor(otherRevision); // handle renames gracefully

    ContentRevision localRevision = file != null ? CurrentContentRevision.create(VcsUtil.getFilePath(file)) : null;
    if (revision == null && localRevision == null) return null;

    if (isAfterRevisionLocal) {
      return new Change(revision, localRevision);
    }
    return new Change(localRevision, revision);
  }

  private static @Nullable VirtualFile getLocalVirtualFileFor(@Nullable ContentRevision revision) {
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

  private static class WithLocalRequestChain extends ChangeDiffRequestChain.Async {
    private final Project myProject;
    private final ListSelection<Change> myChanges;
    private final boolean myUseBeforeVersion;

    private WithLocalRequestChain(@NotNull Project project, @NotNull ListSelection<Change> changes, boolean useBeforeVersion) {
      myProject = project;
      myChanges = changes;
      myUseBeforeVersion = useBeforeVersion;
    }

    @Override
    protected @NotNull ListSelection<? extends ChangeDiffRequestChain.Producer> loadRequestProducers() {
      return myChanges
        .map(change -> getChangeWithLocal(change, myUseBeforeVersion))
        .map(change -> ChangeDiffRequestProducer.create(myProject, change));
    }
  }
}
