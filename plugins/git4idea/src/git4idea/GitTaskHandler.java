// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea;

import com.intellij.dvcs.branch.DvcsTaskHandler;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import git4idea.branch.GitBrancher;
import git4idea.branch.GitBranchesCollection;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static git4idea.branch.GitBranchUtil.sortBranchesByName;

/**
 * @author Dmitry Avdeev
 */
final class GitTaskHandler extends DvcsTaskHandler<GitRepository> {
  @NotNull private final GitBrancher myBrancher;
  @NotNull private final GitRefNameValidator myNameValidator;

  GitTaskHandler(@NotNull Project project) {
    super(GitRepositoryManager.getInstance(project), project, "branch");
    myBrancher = GitBrancher.getInstance(project);
    myNameValidator = GitRefNameValidator.getInstance();
  }

  @Override
  protected void checkout(@NotNull String taskName, @NotNull List<? extends GitRepository> repos, @Nullable Runnable callInAwtLater) {
    myBrancher.checkout(taskName, false, repos, callInAwtLater);
  }

  @Override
  protected void checkoutAsNewBranch(@NotNull String name, @NotNull List<? extends GitRepository> repositories) {
    myBrancher.checkoutNewBranch(name, repositories);
  }

  @Override
  protected String getActiveBranch(GitRepository repository) {
    return repository.getCurrentBranchName();
  }

  @Override
  protected void mergeAndClose(@NotNull String branch, @NotNull List<? extends GitRepository> repositories) {
    myBrancher.merge(branch, GitBrancher.DeleteOnMergeOption.DELETE, repositories);
  }

  @Override
  protected boolean hasBranch(@NotNull GitRepository repository, @NotNull TaskInfo info) {
    GitBranchesCollection branches = repository.getBranches();
    return info.isRemote() ?
           branches.getRemoteBranches().stream().anyMatch(branch -> info.getName().equals(branch.getName())) :
           branches.findLocalBranch(info.getName()) != null;
  }

  @NotNull
  @Override
  protected Iterable<TaskInfo> getAllBranches(@NotNull GitRepository repository) {
    GitBranchesCollection branches = repository.getBranches();
    List<TaskInfo> list = new ArrayList<>(ContainerUtil.map(sortBranchesByName(branches.getLocalBranches()),
                                                            (Function<GitBranch, TaskInfo>)branch -> new TaskInfo(branch.getName(),
                                                                                                                  Collections.singleton(
                                                                                                                    repository
                                                                                                                      .getPresentableUrl()))));
    list.addAll(ContainerUtil.map(sortBranchesByName(branches.getRemoteBranches()),
                                  (Function<GitBranch, TaskInfo>)branch -> new TaskInfo(branch.getName(), Collections.singleton(repository.getPresentableUrl())) {
                                    @Override
                                    public boolean isRemote() {
                                      return true;
                                    }
                                  }));
    return list;
  }

  @Override
  public boolean isBranchNameValid(@NotNull String branchName) {
    return myNameValidator.checkInput(branchName);
  }

  @NotNull
  @Override
  public String cleanUpBranchName(@NotNull String suggestedName) {
    return myNameValidator.cleanUpBranchName(suggestedName);
  }
}
