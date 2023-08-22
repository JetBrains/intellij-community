// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.changes;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsOutgoingChangesProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.GitBranchesSearcher;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.util.Functions.identity;
import static com.intellij.util.containers.ContainerUtil.map;

@Service(Service.Level.PROJECT)
public final class GitOutgoingChangesProvider implements VcsOutgoingChangesProvider<CommittedChangeList> {
  private final static Logger LOG = Logger.getInstance(GitOutgoingChangesProvider.class);
  private final Project myProject;

  public GitOutgoingChangesProvider(Project project) {
    myProject = project;
  }

  @Override
  public Pair<VcsRevisionNumber, List<CommittedChangeList>> getOutgoingChanges(final VirtualFile vcsRoot, final boolean findRemote)
    throws VcsException {
    LOG.debug("getOutgoingChanges root: " + vcsRoot.getPath());
    final GitBranchesSearcher searcher = new GitBranchesSearcher(myProject, vcsRoot, findRemote);
    if (searcher.getLocal() == null || searcher.getRemote() == null) {
      return new Pair<>(null, Collections.emptyList());
    }
    final GitRevisionNumber base = getMergeBase(myProject, vcsRoot, searcher.getLocal(), searcher.getRemote());
    if (base == null) {
      return new Pair<>(null, Collections.emptyList());
    }
    List<GitCommittedChangeList> lists = GitUtil.getLocalCommittedChanges(myProject, vcsRoot,
                                                                          handler -> handler.addParameters(base.asString() + "..HEAD"));
    return new Pair<>(base, map(lists, identity()));
  }

  @Override
  @Nullable
  public VcsRevisionNumber getMergeBaseNumber(final VirtualFile anyFileUnderRoot) throws VcsException {
    LOG.debug("getMergeBaseNumber parameter: " + anyFileUnderRoot.getPath());
    final GitRepository repository = GitRepositoryManager.getInstance(myProject).getRepositoryForFile(anyFileUnderRoot);
    if (repository == null) {
      LOG.info("VCS root not found");
      return null;
    }

    final GitBranchesSearcher searcher = new GitBranchesSearcher(myProject, repository.getRoot(), true);
    if (searcher.getLocal() == null || searcher.getRemote() == null) {
      LOG.info("local or remote not found");
      return null;
    }
    final GitRevisionNumber base = getMergeBase(myProject, repository.getRoot(), searcher.getLocal(), searcher.getRemote());
    LOG.debug("found base: " + ((base == null) ? null : base.asString()));
    return base;
  }

  /**
   * Get a merge base between the current branch and specified branch.
   *
   * @return the common commit or null if the there is no common commit
   */
  @Nullable
  private static GitRevisionNumber getMergeBase(@NotNull Project project, @NotNull VirtualFile root,
                                                @NotNull GitBranch currentBranch, @NotNull GitBranch branch) throws VcsException {
    return GitHistoryUtils.getMergeBase(project, root, currentBranch.getFullName(), branch.getFullName());
  }
}
