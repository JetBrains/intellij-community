/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package git4idea.actions;

import com.intellij.dvcs.actions.DvcsCompareWithBranchAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitBranch;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vcs.history.VcsDiffUtil.createChangesWithCurrentContentForFile;

public class GitCompareWithBranchAction extends DvcsCompareWithBranchAction<GitRepository> {
  @Override
  protected boolean noBranchesToCompare(@NotNull GitRepository repository) {
    int locals = repository.getBranches().getLocalBranches().size();
    boolean haveRemotes = !repository.getBranches().getRemoteBranches().isEmpty();
    if (repository.isOnBranch()) {  // there are other branches to compare
      return locals < 2 && !haveRemotes;
    }
    return locals == 0 && !haveRemotes; // there are at least 1 branch to compare
  }

  @NotNull
  @Override
  protected List<String> getBranchNamesExceptCurrent(@NotNull GitRepository repository) {
    List<GitBranch> localBranches = new ArrayList<GitBranch>(repository.getBranches().getLocalBranches());
    Collections.sort(localBranches);
    List<GitBranch> remoteBranches = new ArrayList<GitBranch>(repository.getBranches().getRemoteBranches());
    Collections.sort(remoteBranches);

    if (repository.isOnBranch()) {
      localBranches.remove(repository.getCurrentBranch());
    }

    List<String> branchNames = ContainerUtil.newArrayList();
    for (GitBranch branch : localBranches) {
      branchNames.add(branch.getName());
    }
    for (GitBranch branch : remoteBranches) {
      branchNames.add(branch.getName());
    }
    return branchNames;
  }

  @NotNull
  @Override
  protected GitRepositoryManager getRepositoryManager(@NotNull Project project) {
    return GitUtil.getRepositoryManager(project);
  }

  @Override
  protected void showDiffWithBranch(@NotNull Project project,
                                    @NotNull VirtualFile file,
                                    @NotNull String head,
                                    @NotNull String branchToCompare) throws VcsException {
    FilePath filePath = VcsUtil.getFilePath(file);
    // we could use something like GitRepository#getCurrentRevision here,
    // but this way we can easily identify if the file is available in the branch
    GitRevisionNumber compareRevisionNumber = (GitRevisionNumber)GitHistoryUtils.getCurrentRevision(project, filePath, branchToCompare);
    if (compareRevisionNumber == null) {
      fileDoesntExistInBranchError(project, file, branchToCompare);
      return;
    }

    GitRevisionNumber currentRevisionNumber = (GitRevisionNumber)GitHistoryUtils.getCurrentRevision(project, filePath, head);
    if (currentRevisionNumber == null) {
      LOG.error(String.format("Current revision number is null for file [%s] and branch [%s]", filePath, head));
      return;
    }
    final GitRepository gitRepository = GitUtil.getRepositoryManager(project).getRepositoryForFile(file);
    if (gitRepository == null) {
      LOG.error("Couldn't find Git Repository for " + file.getName());
      return;
    }

    // constructing the revision with human readable name (will work for files comparison however).
    final GitRevisionNumber compareToRevisionNumber = new GitRevisionNumber(branchToCompare, compareRevisionNumber.getTimestamp());

    Collection<Change> changes =
      GitChangeUtils.getDiff(project, gitRepository.getRoot(), branchToCompare, head, Collections.singletonList(filePath));
    VcsDiffUtil.showDiffFor(project, changes.isEmpty() && !filePath.isDirectory()
                                     ? createChangesWithCurrentContentForFile(filePath,
                                                                              GitContentRevision
                                                                                .createRevision(filePath, compareToRevisionNumber, project,
                                                                                                null))
                                     : changes, VcsDiffUtil.getRevisionTitle(branchToCompare, false),
                            VcsDiffUtil.getRevisionTitle(head, true), filePath);
  }
}
