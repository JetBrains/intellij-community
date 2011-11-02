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
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds Git commits made in a single repository grouped by branches.
 *
 * @author Kirill Likhodedov
 */
public class GitCommitsByBranch {

  private final GitRepository myRepository;
  private final Map<GitBranch, List<GitCommit>> myCommitsByBranch;

  GitCommitsByBranch(GitRepository repository, Map<GitBranch, List<GitCommit>> commitsByBranch) {
    myRepository = repository;
    myCommitsByBranch = commitsByBranch;
  }

  public boolean isEmpty() {
    return myCommitsByBranch.isEmpty();
  }

  public Map<GitBranch, List<GitCommit>> asMap() {
    return myCommitsByBranch;
  }

  public static GitCommitsByBranch fromSingleBranch(GitRepository repository, GitBranch branch, List<GitCommit> commits) {
    return new GitCommitsByBranch(repository, Collections.singletonMap(branch, commits));
  }
}
