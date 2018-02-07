/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
 * @author Dmitry Avdeev
 */
public class GitTaskHandler extends DvcsTaskHandler<GitRepository> {

  @NotNull private final GitBrancher myBrancher;
  @NotNull private final GitRefNameValidator myNameValidator;

  public GitTaskHandler(@NotNull GitBrancher brancher, @NotNull GitRepositoryManager repositoryManager, @NotNull Project project) {
    super(repositoryManager, project, "branch");
    myBrancher = brancher;
    myNameValidator = GitRefNameValidator.getInstance();
  }

  @Override
  protected void checkout(@NotNull String taskName, @NotNull List<GitRepository> repos, @Nullable Runnable callInAwtLater) {
    myBrancher.checkout(taskName, false, repos, callInAwtLater);
  }

  @Override
  protected void checkoutAsNewBranch(@NotNull String name, @NotNull List<GitRepository> repositories) {
    myBrancher.checkoutNewBranch(name, repositories);
  }

  @Override
  protected String getActiveBranch(GitRepository repository) {
    return repository.getCurrentBranchName();
  }

  @Override
  protected void mergeAndClose(@NotNull String branch, @NotNull List<GitRepository> repositories) {
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
    List<TaskInfo> list = new ArrayList<>(ContainerUtil.map(branches.getLocalBranches(),
                                                            (Function<GitBranch, TaskInfo>)branch -> new TaskInfo(branch.getName(),
                                                                                                                  Collections.singleton(
                                                                                                                    repository
                                                                                                                      .getPresentableUrl()))));
    list.addAll(ContainerUtil.map(branches.getRemoteBranches(),
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
