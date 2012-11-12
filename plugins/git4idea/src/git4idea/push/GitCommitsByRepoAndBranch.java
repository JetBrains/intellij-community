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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

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

  /**
   * Copy constructor.
   */
  GitCommitsByRepoAndBranch(GitCommitsByRepoAndBranch original) {
    this(new HashMap<GitRepository, GitCommitsByBranch>(original.myCommitsByRepository));
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

  void clear() {
    myCommitsByRepository.clear();
  }
}

