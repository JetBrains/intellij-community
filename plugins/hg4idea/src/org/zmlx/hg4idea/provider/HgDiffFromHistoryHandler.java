/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.history.CurrentRevision;
import com.intellij.openapi.vcs.history.DiffFromHistoryHandler;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.List;

/**
 * {@link com.intellij.openapi.vcs.history.DiffFromHistoryHandler#showDiffForTwo(com.intellij.openapi.vcs.FilePath, com.intellij.openapi.vcs.history.VcsFileRevision, com.intellij.openapi.vcs.history.VcsFileRevision) "Show Diff" for 2 revision} calls the common code.
 *
 * @author Nadya Zabrodina
 */
public class HgDiffFromHistoryHandler implements DiffFromHistoryHandler {

  private static final Logger LOG = Logger.getInstance(HgDiffFromHistoryHandler.class);

  @NotNull private final Project myProject;

  public HgDiffFromHistoryHandler(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void showDiffForOne(@NotNull AnActionEvent e, @NotNull FilePath filePath,
                             @NotNull VcsFileRevision previousRevision, @NotNull VcsFileRevision revision) {
    doShowDiff(filePath, previousRevision, revision, false);
  }


  @Override
  public void showDiffForTwo(@NotNull FilePath filePath, @NotNull VcsFileRevision revision1, @NotNull VcsFileRevision revision2) {
    doShowDiff(filePath, revision1, revision2, true);
  }

  private void doShowDiff(@NotNull FilePath filePath, @NotNull VcsFileRevision revision1, @NotNull VcsFileRevision revision2,
                          boolean autoSort) {
    if (!filePath.isDirectory()) {
      VcsHistoryUtil.showDifferencesInBackground(myProject, filePath, revision1, revision2, autoSort);
    }
    else if (revision2 instanceof CurrentRevision) {
      HgFileRevision left = (HgFileRevision)revision1;
      showDiffForDirectory(filePath, left, null);
    }
    else if (revision1.equals(VcsFileRevision.NULL)) {
      HgFileRevision right = (HgFileRevision)revision2;
      showDiffForDirectory(filePath, null, right);
    }
    else {
      HgFileRevision left = (HgFileRevision)revision1;
      HgFileRevision right = (HgFileRevision)revision2;
      if (autoSort) {
        Pair<VcsFileRevision, VcsFileRevision> pair = VcsHistoryUtil.sortRevisions(revision1, revision2);
        left = (HgFileRevision)pair.first;
        right = (HgFileRevision)pair.second;
      }
      showDiffForDirectory(filePath, left, right);
    }
  }

  private void showDiffForDirectory(@NotNull final FilePath path,
                                    @Nullable final HgFileRevision rev1,
                                    @Nullable final HgFileRevision rev2) {
    VirtualFile root = VcsUtil.getVcsRootFor(myProject, path);
    LOG.assertTrue(root != null, "Repository is null for " + path);
    calculateDiffInBackground(root, path, rev1, rev2, new Consumer<List<Change>>() {
      @Override
      public void consume(List<Change> changes) {
        showDirDiffDialog(path, rev1 != null ? rev1.getRevisionNumber().getChangeset() : null,
                          rev2 != null ? rev2.getRevisionNumber().getChangeset() : null, changes);
      }
    });
  }

  // rev1 == null => rev2 is the initial commit
  // rev2 == null => comparing rev1 with local
  private void calculateDiffInBackground(@NotNull final VirtualFile root, @NotNull final FilePath path,
                                         @Nullable final HgFileRevision rev1, @Nullable final HgFileRevision rev2,
                                         final Consumer<List<Change>> successHandler) {
    new Task.Backgroundable(myProject, "Comparing revisions...") {
      private List<Change> myChanges;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        if (myProject != null) {
          myChanges = HgUtil.getDiff(HgDiffFromHistoryHandler.this.myProject, root, path, rev1, rev2);
        }
      }

      @Override
      public void onSuccess() {
        successHandler.consume(myChanges);
      }
    }.queue();
  }

  private void showDirDiffDialog(@NotNull FilePath path, @Nullable String hash1, @Nullable String hash2, @NotNull List<Change> diff) {
    DialogBuilder dialogBuilder = new DialogBuilder(myProject);
    String title;
    if (hash2 != null) {
      if (hash1 != null) {
        title = String.format("Difference between %s and %s in %s", hash1, hash2, path.getName());
      }
      else {
        title = String.format("Initial commit %s in %s", hash2, path.getName());
      }
    }
    else {
      LOG.assertTrue(hash1 != null, "hash1 and hash2 can't both be null. Path: " + path);
      title = String.format("Difference between %s and local version in %s", hash1, path.getName());
    }
    dialogBuilder.setTitle(title);
    dialogBuilder.setActionDescriptors(new DialogBuilder.ActionDescriptor[]{new DialogBuilder.CloseDialogAction()});
    final ChangesBrowser changesBrowser = new ChangesBrowser(myProject, null, diff, null, false, true,
                                                             null, ChangesBrowser.MyUseCase.COMMITTED_CHANGES, null);
    changesBrowser.setChangesToDisplay(diff);
    dialogBuilder.setCenterPanel(changesBrowser);
    dialogBuilder.showNotModal();
  }
}

