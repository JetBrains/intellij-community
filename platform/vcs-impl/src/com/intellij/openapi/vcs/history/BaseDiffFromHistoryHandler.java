// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class BaseDiffFromHistoryHandler<T extends VcsFileRevision> implements DiffFromHistoryHandler {

  private static final Logger LOG = Logger.getInstance(BaseDiffFromHistoryHandler.class);

  @NotNull protected final Project myProject;

  protected BaseDiffFromHistoryHandler(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void showDiffForOne(@NotNull AnActionEvent e,
                             @NotNull Project project, @NotNull FilePath filePath,
                             @NotNull VcsFileRevision previousRevision,
                             @NotNull VcsFileRevision revision) {
    doShowDiff(filePath, previousRevision, revision);
  }

  @Override
  public void showDiffForTwo(@NotNull Project project,
                             @NotNull FilePath filePath,
                             @NotNull VcsFileRevision older,
                             @NotNull VcsFileRevision newer) {
    doShowDiff(filePath, older, newer);
  }

  @SuppressWarnings("unchecked")
  protected void doShowDiff(@NotNull FilePath filePath,
                            @NotNull VcsFileRevision older,
                            @NotNull VcsFileRevision newer) {
    if (!filePath.isDirectory()) {
      VcsHistoryUtil.showDifferencesInBackground(myProject, filePath, older, newer);
    }
    else if (older.equals(VcsFileRevision.NULL)) {
      T right = (T)newer;
      showAffectedChanges(filePath, right);
    }
    else if (newer instanceof CurrentRevision) {
      T left = (T)older;
      showChangesBetweenRevisions(filePath, left, null);
    }
    else {
      T left = (T)older;
      T right = (T)newer;
      showChangesBetweenRevisions(filePath, left, right);
    }
  }

  protected void showChangesBetweenRevisions(@NotNull final FilePath path, @NotNull final T older, @Nullable final T newer) {
    if (newer == null) {
      FileDocumentManager.getInstance().saveAllDocuments();
    }
    new CollectChangesTask(VcsBundle.message("file.history.diff.handler.comparing.process")) {

      @NotNull
      @Override
      public List<Change> getChanges() throws VcsException {
        return getChangesBetweenRevisions(path, older, newer);
      }

      @NotNull
      @Override
      public String getDialogTitle() {
        return getChangesBetweenRevisionsDialogTitle(path, older, newer);
      }
    }.queue();
  }

  protected void showAffectedChanges(@NotNull final FilePath path, @NotNull final T rev) {
    new CollectChangesTask(VcsBundle.message("file.history.diff.handler.collecting.affected.process")) {

      @NotNull
      @Override
      public List<Change> getChanges() throws VcsException {
        return getAffectedChanges(path, rev);
      }

      @NotNull
      @Override
      public String getDialogTitle() {
        return getAffectedChangesDialogTitle(path, rev);
      }
    }.queue();
  }

  // rev2 == null -> compare rev1 with local
  // rev2 != null -> compare rev1 with rev2
  @NotNull
  protected abstract List<Change> getChangesBetweenRevisions(@NotNull final FilePath path, @NotNull final T rev1, @Nullable final T rev2)
    throws VcsException;

  @NotNull
  protected abstract List<Change> getAffectedChanges(@NotNull final FilePath path, @NotNull final T rev) throws VcsException;

  @NotNull
  protected abstract String getPresentableName(@NotNull T revision);

  protected void showChangesDialog(@NotNull @Nls String title, @NotNull List<? extends Change> changes) {
    VcsDiffUtil.showChangesDialog(myProject, title, changes);
  }

  protected void showError(@NotNull VcsException e, @NotNull @Nls String logMessage) {
    LOG.info(logMessage, e);
    VcsBalloonProblemNotifier.showOverVersionControlView(myProject, e.getMessage(), MessageType.ERROR);
  }

  @Nls
  @NotNull
  protected String getChangesBetweenRevisionsDialogTitle(@NotNull final FilePath path, @NotNull final T rev1, @Nullable final T rev2) {
    String rev1Title = getPresentableName(rev1);
    if (rev2 == null) {
      return VcsBundle.message("file.history.diff.handler.paths.diff.with.local.title", rev1Title, path.getName());
    }

    String rev2Title = getPresentableName(rev2);
    return VcsBundle.message("file.history.diff.handler.paths.diff.title", rev1Title, rev2Title, path.getName());
  }

  @Nls
  @NotNull
  protected String getAffectedChangesDialogTitle(@NotNull final FilePath path, @NotNull final T rev) {
    return VcsBundle.message("file.history.diff.handler.affected.changes.title", getPresentableName(rev), path.getName());
  }

  protected abstract class CollectChangesTask extends Task.Backgroundable {

    private List<Change> myChanges;

    public CollectChangesTask(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String title) {
      super(BaseDiffFromHistoryHandler.this.myProject, title);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        myChanges = getChanges();
      }
      catch (VcsException e) {
        showError(e, VcsBundle.message("file.history.diff.handler.process.error", getDialogTitle()));
      }
    }

    @NotNull
    public abstract List<Change> getChanges() throws VcsException;

    @Nls
    @NotNull
    public abstract String getDialogTitle();

    @Override
    public void onSuccess() {
      showChangesDialog(getDialogTitle(), ContainerUtil.notNullize(myChanges));
    }
  }
}
