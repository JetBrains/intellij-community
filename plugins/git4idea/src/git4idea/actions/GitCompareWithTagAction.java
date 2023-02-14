// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.dvcs.actions.DvcsCompareWithAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.GitNotificationIdsHolder;
import git4idea.GitRevisionNumber;
import git4idea.GitTag;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vcs.history.VcsDiffUtil.createChangesWithCurrentContentForFile;

public class GitCompareWithTagAction extends DvcsCompareWithAction<GitRepository> {
  @Override
  protected boolean nothingToCompare(@NotNull GitRepository repository) {
    return false;
  }

  @NotNull
  @Override
  protected GitRepositoryManager getRepositoryManager(@NotNull Project project) {
    return GitUtil.getRepositoryManager(project);
  }

  @Override
  protected @Nullable JBPopup createPopup(@NotNull Project project, @NotNull GitRepository repository, @NotNull VirtualFile file) {
    List<String> tags;
    try {
      tags = VcsUtil.computeWithModalProgress(project,
                                              GitBundle.message("git.compare.with.tag.modal.progress.loading.tags"),
                                              true,
                                              (indicator) -> GitBranchUtil.getAllTags(project, repository.getRoot()));
    }
    catch (VcsException e) {
      VcsNotifier.getInstance(project).notifyError(GitNotificationIdsHolder.TAGS_LOADING_FAILED,
                                                   GitBundle.message("git.compare.with.tag.loading.error.title"),
                                                   e.getMessage());
      return null;
    }

    return createPopup(GitBundle.message("git.compare.with.tag.popup.title"), tags,
                       selected -> showDiffWithTag(project, repository, file, selected));
  }

  private static void showDiffWithTag(@NotNull Project project,
                                      @NotNull GitRepository repository,
                                      @NotNull VirtualFile file,
                                      @NotNull @NlsSafe String tagName) {
    String presentableRevisionName = getPresentableCurrentBranchName(repository);
    String revNumTitle1 = VcsDiffUtil.getRevisionTitle(tagName, false);
    String revNumTitle2 = VcsDiffUtil.getRevisionTitle(presentableRevisionName, true);
    showDiffBetweenRevision(project, file, revNumTitle1, revNumTitle2, () -> getDiffChanges(project, repository, file, tagName));
  }

  @NotNull
  private static Collection<Change> getDiffChanges(@NotNull Project project,
                                                   @NotNull GitRepository repository,
                                                   @NotNull VirtualFile file,
                                                   @NotNull String tagName) throws VcsException {
    FilePath filePath = VcsUtil.getFilePath(file);
    String refToCompare = GitTag.REFS_TAGS_PREFIX + tagName;
    GitRevisionNumber revisionNumber = new GitRevisionNumber(refToCompare);

    Collection<Change> changes =
      GitChangeUtils.getDiffWithWorkingDir(project, repository.getRoot(), refToCompare, Collections.singletonList(filePath), false);

    // if git returned no changes, we need to check that file exists in compareWith branch to avoid this error in diff dialog
    // a.e. when you perform compareWith for unversioned file
    if (changes.isEmpty() && GitHistoryUtils.getCurrentRevision(project, filePath, refToCompare) == null) {
      throw new VcsException(GitBundle.message("git.compare.with.tag.file.not.found.in.tag",
                                               file.isDirectory() ? 2 : 1, file.getPresentableUrl(), tagName));
    }

    ContentRevision revision = GitContentRevision.createRevision(filePath, revisionNumber, project);
    return changes.isEmpty() && !filePath.isDirectory() ? createChangesWithCurrentContentForFile(filePath, revision) : changes;
  }
}
