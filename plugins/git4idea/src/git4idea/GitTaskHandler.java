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

import com.intellij.openapi.vcs.VcsTaskHandler;
import git4idea.branch.GitBrancher;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

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
  public void startNewTask(String name) {
    List<GitRepository> repositories = myRepositoryManager.getRepositories();
    myBrancher.checkoutNewBranch(name, repositories);
  }

  @Override
  public void switchTask(String name) {
    myBrancher.checkout(name, myRepositoryManager.getRepositories(), null);
  }

  @Override
  public void closeTask(String name) {
    myBrancher.merge(name, GitBrancher.DeleteOnMergeOption.DELETE, myRepositoryManager.getRepositories());
  }
}
