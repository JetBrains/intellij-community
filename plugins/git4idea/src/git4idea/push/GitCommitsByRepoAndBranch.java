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

import git4idea.GitBranch;
import git4idea.GitCommit;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Holds Git commits grouped by repositories and by branches.
 * Actually, it is just a map (of maps of lists of commits) encapsulated in a separated class with some handy methods.
 *
 * @author Kirill Likhodedov
 */
final class GitCommitsByRepoAndBranch {

  private final Map<GitRepository, GitCommitsByBranch> myCommitsByRepository;

  GitCommitsByRepoAndBranch(@NotNull Map<GitRepository, GitCommitsByBranch> commitsByRepository) {
    myCommitsByRepository = commitsByRepository;
  }

  @NotNull
  static GitCommitsByRepoAndBranch empty() {
    return new GitCommitsByRepoAndBranch(new HashMap<GitRepository, GitCommitsByBranch>());
  }

  @NotNull
  Collection<GitRepository> getRepositories() {
    return new HashSet<GitRepository>(myCommitsByRepository.keySet());
  }

  @NotNull
  GitCommitsByBranch get(@NotNull GitRepository repository) {
    return new GitCommitsByBranch(myCommitsByRepository.get(repository));
  }

  /**
   * Creates new GitCommitByRepoAndBranch structure with only those repositories, which exist in the specified collection.
   */
  @NotNull
  GitCommitsByRepoAndBranch retainAll(@NotNull Collection<GitRepository> repositories) {
    Map<GitRepository, GitCommitsByBranch> commits = new HashMap<GitRepository, GitCommitsByBranch>();
    for (GitRepository selectedRepository : repositories) {
      GitCommitsByBranch value = myCommitsByRepository.get(selectedRepository);
      if (value != null) {
        commits.put(selectedRepository, value);
      }
    }
    return new GitCommitsByRepoAndBranch(commits);
  }

  /**
   * Creates new GitCommitByRepoAndBranch structure with only those pairs repository-branch, which exist in the specified map.
   */
  @NotNull
  GitCommitsByRepoAndBranch retainAll(@NotNull Map<GitRepository, GitBranch> repositoriesBranches) {
    Map<GitRepository, GitCommitsByBranch> commits = new HashMap<GitRepository, GitCommitsByBranch>();
    for (GitRepository repository : repositoriesBranches.keySet()) {
      GitCommitsByBranch commitsByBranch = myCommitsByRepository.get(repository);
      if (commitsByBranch != null) {
        commits.put(repository, commitsByBranch.retain(repositoriesBranches.get(repository)));
      }
    }
    return new GitCommitsByRepoAndBranch(commits);
  }

  @NotNull
  public Collection<GitCommit> getAllCommits() {
    Collection<GitCommit> commits = new ArrayList<GitCommit>();
    for (GitCommitsByBranch commitsByBranch : myCommitsByRepository.values()) {
      commits.addAll(commitsByBranch.getAllCommits());
    }
    return commits;
  }

}

