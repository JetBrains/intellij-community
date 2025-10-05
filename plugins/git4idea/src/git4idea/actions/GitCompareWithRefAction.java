// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions;

import com.intellij.dvcs.actions.DvcsCompareWithAction;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.git.repo.GitRepositoriesHolder;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.*;
import git4idea.GitTag;
import git4idea.changes.GitChangeUtils;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.branch.compareWith.GitCompareWithBranchPopup;
import git4idea.ui.branch.compareWith.GitCompareWithBranchPopupStep;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;

import static com.intellij.openapi.vcs.history.VcsDiffUtil.createChangesWithCurrentContentForFile;

public class GitCompareWithRefAction extends DvcsCompareWithAction<GitRepository> {
  @Override
  protected @NotNull JBPopup createPopup(@NotNull Project project, @NotNull GitRepository repository, @NotNull VirtualFile file) {
    Consumer<GitReference> selectionHandler = selected -> showDiff(repository, file, selected);
    return new GitCompareWithBranchPopup(project,
                                         new GitCompareWithBranchPopupStep(project,
                                                                           GitRepositoriesHolder.Companion.getInstance(project)
                                                                             .get(repository.getRpcId()), selectionHandler));
  }

  @Override
  protected boolean nothingToCompare(@NotNull GitRepository repository) {
    int locals = repository.getBranches().getLocalBranches().size();
    boolean haveRemotes = !repository.getBranches().getRemoteBranches().isEmpty();
    if (repository.isOnBranch()) {  // there are other branches to compare
      return locals < 2 && !haveRemotes;
    }
    return locals == 0 && !haveRemotes; // there are at least 1 branch to compare
  }

  @Override
  protected @NotNull GitRepositoryManager getRepositoryManager(@NotNull Project project) {
    return GitUtil.getRepositoryManager(project);
  }

  public static @NotNull Collection<Change> getDiffChanges(@NotNull GitRepository repository,
                                                           @NotNull VirtualFile file,
                                                           @NotNull GitReference refToCompare) throws VcsException {
    FilePath filePath = VcsUtil.getFilePath(file);
    GitRevisionNumber revisionNumber = new GitRevisionNumber(refToCompare.getFullName());

    Project project = repository.getProject();
    Collection<Change> changes =
      GitChangeUtils.getDiffWithWorkingDir(project, repository.getRoot(), refToCompare.getFullName(), Collections.singletonList(filePath), false);

    // if git returned no changes, we need to check that file exists in compareWith branch to avoid this error in diff dialog
    // a.e. when you perform compareWith for unversioned file
    if (changes.isEmpty() && GitHistoryUtils.getCurrentRevision(project, filePath, refToCompare.getFullName()) == null) {
      throw new VcsException(getErrorMessage(file, refToCompare));
    }

    ContentRevision contentRevision = GitContentRevision.createRevision(filePath, revisionNumber, project);
    return changes.isEmpty() && !filePath.isDirectory() ? createChangesWithCurrentContentForFile(filePath, contentRevision) : changes;
  }

  private static void showDiff(@NotNull GitRepository repository,
                               @NotNull VirtualFile file,
                               @NotNull GitReference reference) {
    String presentableRevisionName = getPresentableCurrentBranchName(repository);
    String revNumTitle1 = VcsDiffUtil.getRevisionTitle(reference.getName(), false);
    String revNumTitle2 = VcsDiffUtil.getRevisionTitle(presentableRevisionName, true);
    showDiffBetweenRevision(repository.getProject(), file, revNumTitle1, revNumTitle2, () -> getDiffChanges(repository, file, reference));
  }

  private static @NotNull @Nls String getErrorMessage(@NotNull VirtualFile file, @NotNull GitReference refToCompare) {
    int choice = file.isDirectory() ? 2 : 1;
    return refToCompare instanceof GitTag ?
           GitBundle.message("git.compare.with.tag.file.not.found.in.tag",
                                            choice, file.getPresentableUrl(), refToCompare.getName()) :
           DvcsBundle.message("error.text.file.not.found.in.branch",
                                             choice, file.getPresentableUrl(), refToCompare.getName());
  }
}
