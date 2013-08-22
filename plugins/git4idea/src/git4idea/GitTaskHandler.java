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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Dmitry Avdeev
 *         Date: 17.07.13
 */
public class GitTaskHandler extends VcsTaskHandler {

  private final GitBrancher myBrancher;
  private final GitRepositoryManager myRepositoryManager;
  private final Project myProject;

  public GitTaskHandler(GitBrancher brancher, GitRepositoryManager repositoryManager, Project project) {
    myBrancher = brancher;
    myRepositoryManager = repositoryManager;
    myProject = project;
  }

  @Override
  public TaskInfo startNewTask(final String taskName) {
    List<GitRepository> repositories = myRepositoryManager.getRepositories();
    List<GitRepository> problems = ContainerUtil.filter(repositories, new Condition<GitRepository>() {
      @Override
      public boolean value(GitRepository repository) {
        return repository.getBranches().findLocalBranch(taskName) != null;
      }
    });
    MultiMap<String, String> map = new MultiMap<String, String>();
    if (!problems.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode() ||
          Messages.showDialog(myProject, "<html>The following repositories already have specified branch <b>" + taskName + "</b>:<br>" +
                                  StringUtil.join(problems, "<br>") + ".<br>" +
                                  "Do you want to checkout existing branch?", "Branch Already Exists",
                                  new String[]{Messages.YES_BUTTON, Messages.NO_BUTTON}, 0,
                                  Messages.getWarningIcon(), new DialogWrapper.PropertyDoNotAskOption("git.checkout.existing.branch")) == 0) {
        myBrancher.checkout(taskName, problems, null);
        fillMap(taskName, problems, map);
      }
    }
    repositories.removeAll(problems);
    if (!repositories.isEmpty()) {
      myBrancher.checkoutNewBranch(taskName, repositories);
    }

    fillMap(taskName, repositories, map);
    return new TaskInfo(map);
  }

  private static void fillMap(String taskName, List<GitRepository> repositories, MultiMap<String, String> map) {
    for (GitRepository repository : repositories) {
      map.putValue(taskName, repository.getPresentableUrl());
    }
  }

  @Override
  public void switchToTask(TaskInfo taskInfo) {
    for (final String branchName : taskInfo.branches.keySet()) {
      List<GitRepository> repositories = getRepositories(taskInfo.branches.get(branchName));
      List<GitRepository> notFound = ContainerUtil.filter(repositories, new Condition<GitRepository>() {
        @Override
        public boolean value(GitRepository repository) {
          return repository.getBranches().findLocalBranch(branchName) == null;
        }
      });
      if (!notFound.isEmpty()) {
        myBrancher.checkoutNewBranch(branchName, notFound);
      }
      repositories.removeAll(notFound);
      if (!repositories.isEmpty()) {
        myBrancher.checkout(branchName, repositories, null);
      }
    }
  }

  @Override
  public void closeTask(final TaskInfo taskInfo, TaskInfo original) {

    Set<String> branches = original.branches.keySet();
    final AtomicInteger counter = new AtomicInteger(branches.size());
    for (final String originalBranch : branches) {
      myBrancher.checkout(originalBranch, getRepositories(original.branches.get(originalBranch)), new Runnable() {
        @Override
        public void run() {
          if (counter.decrementAndGet() == 0) {
            merge(taskInfo);
          }
        }
      });
    }
  }

  private void merge(TaskInfo taskInfo) {
    for (String featureBranch : taskInfo.branches.keySet()) {
      myBrancher.merge(featureBranch, GitBrancher.DeleteOnMergeOption.DELETE, getRepositories(taskInfo.branches.get(featureBranch)));
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
