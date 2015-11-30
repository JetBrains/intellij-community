/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.containers.ContainerUtil;
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
    new CollectChangesTask("Comparing revisions...") {

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
    new CollectChangesTask("Collecting affected changes...") {

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

  protected void showChangesDialog(@NotNull String title, @NotNull List<Change> changes) {
    VcsDiffUtil.showChangesDialog(myProject, title, changes);
  }

  protected void showError(@NotNull VcsException e, @NotNull String logMessage) {
    LOG.info(logMessage, e);
    VcsBalloonProblemNotifier.showOverVersionControlView(myProject, e.getMessage(), MessageType.ERROR);
  }

  @NotNull
  protected String getChangesBetweenRevisionsDialogTitle(@NotNull final FilePath path, @NotNull final T rev1, @Nullable final T rev2) {
    String rev1Title = getPresentableName(rev1);

    return rev2 != null
           ? String.format("Difference between %s and %s in %s", rev1Title, getPresentableName(rev2), path.getName())
           : String.format("Difference between %s and local version in %s", rev1Title, path.getName());
  }

  @NotNull
  protected String getAffectedChangesDialogTitle(@NotNull final FilePath path, @NotNull final T rev) {
    return String.format("Initial commit %s in %s", getPresentableName(rev), path.getName());
  }

  protected abstract class CollectChangesTask extends Task.Backgroundable {

    private List<Change> myChanges;

    public CollectChangesTask(@NotNull String title) {
      super(BaseDiffFromHistoryHandler.this.myProject, title);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        myChanges = getChanges();
      }
      catch (VcsException e) {
        showError(e, "Error during task: " + getDialogTitle());
      }
    }

    @NotNull
    public abstract List<Change> getChanges() throws VcsException;

    @NotNull
    public abstract String getDialogTitle();

    @Override
    public void onSuccess() {
      showChangesDialog(getDialogTitle(), ContainerUtil.notNullize(myChanges));
    }
  }
}
