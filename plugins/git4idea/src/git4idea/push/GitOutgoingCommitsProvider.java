// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push;

import com.intellij.dvcs.push.OutgoingCommitsProvider;
import com.intellij.dvcs.push.OutgoingResult;
import com.intellij.dvcs.push.PushSpec;
import com.intellij.dvcs.push.VcsError;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import git4idea.GitCommit;
import git4idea.GitUtil;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class GitOutgoingCommitsProvider extends OutgoingCommitsProvider<GitRepository, GitPushSource, GitPushTarget> {

  private final @NotNull Project myProject;

  public GitOutgoingCommitsProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull OutgoingResult getOutgoingCommits(@NotNull GitRepository repository, @NotNull PushSpec<GitPushSource, GitPushTarget> pushSpec,
                                                    boolean initial) {
    GitPushSource gitPushSource = pushSpec.getSource();
    String source = gitPushSource.getRevision();
    GitPushTarget target = pushSpec.getTarget();
    String destination = target.getBranch().getFullName();
    try {
      List<GitCommit> commits;
      if (!target.isNewBranchCreated()) {
        commits = GitHistoryUtils.history(myProject, repository.getRoot(), destination + ".." + source);
      }
      else {
        commits = GitHistoryUtils.history(myProject, repository.getRoot(),
                                          source, "--not", "--remotes=" + target.getBranch().getRemote().getName(), "--max-count=" + 1000);
      }
      return new OutgoingResult(commits, Collections.emptyList());
    }
    catch (VcsException e) {
      return new OutgoingResult(Collections.emptyList(),
                                Collections.singletonList(new VcsError(GitUtil.cleanupErrorPrefixes(e.getMessage()))));
    }
  }
}
