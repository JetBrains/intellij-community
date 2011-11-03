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

import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds Git commits grouped by repositories and by branches.
 * Actually, it is just a map (of maps of lists of commits) encapsulated in a separated class with some handy methods.
 *
 * @author Kirill Likhodedov
 */
final class GitCommitsByRepoAndBranch {

  private final Map<GitRepository, GitCommitsByBranch> myCommitsByRepository;

  GitCommitsByRepoAndBranch(Map<GitRepository, GitCommitsByBranch> commitsByRepository) {
    myCommitsByRepository = commitsByRepository;
  }

  static GitCommitsByRepoAndBranch empty() {
    return new GitCommitsByRepoAndBranch(Collections.<GitRepository, GitCommitsByBranch>emptyMap());
  }

  @Deprecated
  Map<GitRepository, GitCommitsByBranch> asMap() {
    return myCommitsByRepository;
  }

  @NotNull
  Collection<GitRepository> getRepositories() {
    return myCommitsByRepository.keySet();
  }

  /**
   * Retains only the elements for the given repositories.
   * In other words, removes from this collection all of its elements that correspond to repositories not contained in the specified
   * collection.
   * This object is unaffected, a new object is returned by the method.
   * @return New GitCommitsByRepoAndBranch which contains commits only from repositories listed in {@code repositories}.
   */
  @NotNull
  GitCommitsByRepoAndBranch retainAll(Collection<GitRepository> repositories) {
    Map<GitRepository, GitCommitsByBranch> commits = new HashMap<GitRepository, GitCommitsByBranch>();
    for (GitRepository selectedRepository : repositories) {
      GitCommitsByBranch value = myCommitsByRepository.get(selectedRepository);
      if (value != null) {
        commits.put(selectedRepository, value);
      }
    }
    return new GitCommitsByRepoAndBranch(commits);
  }

  @NotNull
  GitCommitsByBranch get(GitRepository repository) {
    return myCommitsByRepository.get(repository);
  }

  int commitsNumber() {
    int sum = 0;
    for (GitCommitsByBranch commitsByBranch : myCommitsByRepository.values()) {
      sum += commitsByBranch.commitsNumber();
    }
    return sum;
  }

}

