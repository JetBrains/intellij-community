// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitTag;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.push.GitPushParamsImpl;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static java.util.Collections.singletonList;

class GitDeleteRemoteTagOperation extends GitBranchOperation {
  private final String myTagName;

  public GitDeleteRemoteTagOperation(@NotNull Project project, @NotNull Git git,
                                     @NotNull GitBranchUiHandler handler, @NotNull List<GitRepository> repositories,
                                     @NotNull String name) {
    super(project, git, handler, repositories);
    myTagName = name;
  }

  @Override
  protected void execute() {
    String tagFullName = GitTag.REFS_TAGS_PREFIX + myTagName;

    int successRemotes = 0;
    int failureRemotes = 0;

    GitCompoundResult result = new GitCompoundResult(myProject);
    Collection<GitRepository> repositories = getRepositories();
    for (GitRepository repository: repositories) {
      for (GitRemote remote: repository.getRemotes()) {
        GitCommandResult lsRemoteResult = myGit.lsRemoteRefs(myProject, repository.getRoot(), remote, singletonList(tagFullName), "--tags");
        if (!lsRemoteResult.success()) {
          result.append(repository, lsRemoteResult);
          continue;
        }

        if (hasTagOnRemote(tagFullName, lsRemoteResult.getOutput())) {
          GitCommandResult pushResult = myGit.push(repository, new GitPushParamsImpl(remote, ":" + tagFullName,
                                                                                     false, false, false, null));
          result.append(repository, pushResult);

          if (pushResult.success()) {
            successRemotes++;
          }
          else {
            failureRemotes++;
          }
        }
      }

      repository.update();
    }


    boolean hasMultipleRemotes = ContainerUtil.exists(repositories, it -> it.getRemotes().size() > 1);
    String onRemotes = hasMultipleRemotes ? " on remotes" : " on remote";

    if (successRemotes > 0) {
      String title = "Deleted tag " + myTagName + onRemotes;
      VcsNotifier.getInstance(myProject).notifySuccess(title, "");
    }
    else if (successRemotes == 0 && failureRemotes == 0) {
      String message = "Tag " + myTagName + " doesn't exist" + onRemotes;
      VcsNotifier.getInstance(myProject).notifySuccess("", message);
    }

    if (!result.totalSuccess()) {
      String title = "Failed to delete tag " + myTagName + onRemotes;
      VcsNotifier.getInstance(myProject).notifyError(title, result.getErrorOutputWithReposIndication());
    }
  }

  private static boolean hasTagOnRemote(@NotNull String tagFullName, @NotNull List<String> lsRemoteOutput) {
    return ContainerUtil.exists(lsRemoteOutput, line -> {
      if (StringUtil.isEmptyOrSpaces(line)) return false;
      List<String> split = StringUtil.split(line, "\t");
      if (split.size() != 2) return false;
      return tagFullName.equals(split.get(1));
    });
  }

  @Override
  protected void rollback() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getSuccessMessage() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  protected String getRollbackProposal() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  protected String getOperationName() {
    throw new UnsupportedOperationException();
  }
}