// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions;

import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.*;
import git4idea.changes.GitChangeUtils;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.openapi.vcs.history.VcsDiffUtil.createChangesWithCurrentContentForFile;

/**
 * @deprecated Replaced with {@link GitCompareWithRefAction}
 */
@Deprecated(forRemoval = true)
public class GitCompareWithBranchAction {
  /**
   * @deprecated Consider using {@link GitCompareWithRefAction#getDiffChanges(GitRepository, VirtualFile, GitReference)}
   */
  @Deprecated(forRemoval = true)
  protected @NotNull Collection<Change> getDiffChanges(@NotNull Project project, @NotNull VirtualFile file,
                                                       @NotNull String branchName) throws VcsException {
    FilePath filePath = VcsUtil.getFilePath(file);
    final GitRepository gitRepository = GitUtil.getRepositoryForFile(project, file);
    GitBranch branch = gitRepository.getBranches().findBranchByName(branchName);
    String branchToCompare = branch != null ? branch.getFullName() : branchName;

    final VirtualFile gitRepositoryRoot = gitRepository.getRoot();
    GitRevisionNumber compareRevisionNumber = new GitRevisionNumber(branchToCompare);
    Collection<Change> changes =
      GitChangeUtils.getDiffWithWorkingDir(project, gitRepositoryRoot, branchToCompare, Collections.singletonList(filePath), false);
    // if git returned no changes, we need to check that file exists in compareWith branch to avoid this error in diff dialog
    // a.e. when you perform compareWith for unversioned file
    if (changes.isEmpty() && GitHistoryUtils.getCurrentRevision(project, filePath, branchToCompare) == null) {
      throw new VcsException(DvcsBundle.message("error.text.file.not.found.in.branch",
                                                file.isDirectory() ? 2 : 1, file.getPresentableUrl(), branchToCompare));
    }
    ContentRevision revision = GitContentRevision.createRevision(filePath, compareRevisionNumber, project);
    return changes.isEmpty() && !filePath.isDirectory() ? createChangesWithCurrentContentForFile(filePath, revision) : changes;
  }
}
