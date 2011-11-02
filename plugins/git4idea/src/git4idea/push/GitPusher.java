/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.push;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import git4idea.GitBranch;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Collects information to push and performs the push.
 *
 * @author Kirill Likhodedov
 */
class GitPusher {

  private final Project myProject;
  private final Collection<GitRepository> myRepositories;

  GitPusher(Project project) {
    myProject = project;
    myRepositories = GitRepositoryManager.getInstance(project).getRepositories();
  }

  /**
   *
   *
   * @param repositoriesToBranches which branches in which repositories should be pushed.
   *                               The most common situation is all repositories in the project with a single currently active branch for
   *                               each of them.
   * @return
   */
  @NotNull
  public GitCommitsByRepoAndBranch collectCommitsToPush(Map<GitRepository, Collection<GitBranch>> repositoriesToBranches) {
    Map<GitRepository, GitCommitsByBranch> commitsByRepoAndBranch = new HashMap<GitRepository, GitCommitsByBranch>();
    for (GitRepository repository : myRepositories) {
      GitCommitsByBranch commitsByBranch = collectsCommitsToPush(repository, repositoriesToBranches.get(repository));
      if (!commitsByBranch.isEmpty()) {
        commitsByRepoAndBranch.put(repository, commitsByBranch);
      }
    }
    return new GitCommitsByRepoAndBranch(myProject, commitsByRepoAndBranch);
  }

  private GitCommitsByBranch collectsCommitsToPush(GitRepository repository, Collection<GitBranch> branches) {
    Map<GitBranch, List<GitCommit>> commitsByBranch = new HashMap<GitBranch, List<GitCommit>>();

    for (GitBranch branch : branches) {
      List<GitCommit> commits = collectCommitsToPushToTrackedBranch(repository, branch);
      if (!commits.isEmpty()) {
        commitsByBranch.put(branch, commits);
      }
    }

    return new GitCommitsByBranch(repository, commitsByBranch);
  }

  @NotNull
  private List<GitCommit> collectCommitsToPushToTrackedBranch(GitRepository repository, GitBranch branch) {
    // TODO exceptions handling
    try {
      GitBranch trackedBranch = branch.tracked(myProject, repository.getRoot());
      if (trackedBranch == null) {
        return Collections.emptyList();
      }
      return GitHistoryUtils.history(myProject, repository.getRoot(), trackedBranch.getName() + ".." + branch.getName());
    }
    catch (VcsException e) {
      e.printStackTrace();
    }
    return Collections.emptyList();
  }
  
  //public List<GitChangesHolder> collectCommitsToPushAsNodes(Map<GitRepository, Collection<GitBranch>> repositoriesToBranches) {
  //  // TODO if there are several repositories, display them. If there is only one, don't
  //  // If there are several branches to push, display them. If there is only one, don't.
  //
  //  GitCommitsByRepoAndBranch commitsByRepoAndBranch = collectCommitsToPush(repositoriesToBranches);
  //  List<GitChangesHolder> holders = new ArrayList<GitChangesHolder>();
  //  for (Map.Entry<GitRepository, GitCommitsByBranch> entry : commitsByRepoAndBranch.asMap().entrySet()) {
  //    GitRepository repository = entry.getKey();
  //    // TODO if there is one branch to push in this repository, then clicking on the repo should show the list of commits to push
  //    // but if there are several branches, then it has no sense to display anything there.
  //    holders.add(new GitRepositoryNode(repository, Collections.<Change>emptyList()));
  //
  //    for (Map.Entry<GitBranch, List<GitCommit>> en : entry.getValue().asMap().entrySet()) {
  //      GitBranch branch = en.getKey();
  //      holders.add(new GitBranchNode(branch, Collections.<Change>emptyList()));
  //
  //      for (GitCommit gitCommit : en.getValue()) {
  //        holders.add(new GitCommitNode(gitCommit));
  //      }
  //    }
  //  }
  //  return holders;
  //}
  

}
