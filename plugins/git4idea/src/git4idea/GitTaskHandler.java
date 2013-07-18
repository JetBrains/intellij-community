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

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import git4idea.branch.GitBrancher;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 17.07.13
 */
public class GitTaskHandler extends VcsTaskHandler {

  private final GitBrancher myBrancher;
  private final GitRepositoryManager myRepositoryManager;

  public GitTaskHandler(GitBrancher brancher, GitRepositoryManager repositoryManager) {
    myBrancher = brancher;
    myRepositoryManager = repositoryManager;
  }

  @Override
  public TaskInfo startNewTask(final String taskName) {
    List<GitRepository> repositories = myRepositoryManager.getRepositories();
    myBrancher.checkoutNewBranch(taskName, repositories);

    MultiMap<String, String> map = new MultiMap<String, String>();
    for (GitRepository repository : repositories) {
      map.putValue(taskName, repository.getPresentableUrl());
    }
    return new TaskInfo(map);
  }

  @Override
  public void switchToTask(TaskInfo taskInfo) {
    for (String branchName : taskInfo.branches.keySet()) {
      myBrancher.checkout(branchName, getRepositories(taskInfo.branches.get(branchName)), null);
    }
  }

  @Override
  public void closeTask(TaskInfo taskInfo) {

    for (String branchName : taskInfo.branches.keySet()) {
      myBrancher.merge(branchName, GitBrancher.DeleteOnMergeOption.DELETE, getRepositories(taskInfo.branches.get(branchName)));
    }
  }

  @Override
  public TaskInfo getActiveTask() {
    List<GitRepository> repositories = myRepositoryManager.getRepositories();

    MultiMap<String, String> branches = new MultiMap<String, String>();
    for (GitRepository repository : repositories) {
      GitLocalBranch branch = repository.getCurrentBranch();
      if (branch != null) {
        branches.putValue(branch.getName(), repository.getPresentableUrl());
      }
    }
    return new TaskInfo(branches);
  }

  private List<GitRepository> getRepositories(Collection<String> urls) {
    final List<GitRepository> repositories = myRepositoryManager.getRepositories();
    return ContainerUtil.mapNotNull(urls, new NullableFunction<String, GitRepository>() {
      @Nullable
      @Override
      public GitRepository fun(final String s) {

        return ContainerUtil.find(repositories, new Condition<GitRepository>() {
          @Override
          public boolean value(GitRepository repository) {
            return s.equals(repository.getPresentableUrl());
          }
        });
      }
    });
  }
}
