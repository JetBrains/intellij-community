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
import git4idea.GitBranch;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds Git commits grouped by repositories and by branches.
 *
 * @author Kirill Likhodedov
 */
public class GitCommitsByRepoAndBranch {

  private final Project myProject;
  private final Map<GitRepository, GitCommitsByBranch> myCommitsByRepository;

  public GitCommitsByRepoAndBranch(Project project, Map<GitRepository, GitCommitsByBranch> commitsByRepository) {
    myCommitsByRepository = commitsByRepository;
    myProject = project;
  }

  public Map<GitRepository, GitCommitsByBranch> asMap() {
    return myCommitsByRepository;
  }

  public static GitCommitsByRepoAndBranch empty(Project project) {
    return new GitCommitsByRepoAndBranch(project, Collections.<GitRepository, GitCommitsByBranch>emptyMap());
  }

  public static GitCommitsByRepoAndBranch fromSingleRepoAndBranch(Project project, GitRepository repository, GitBranch branch, List<GitCommit> commits) {
    return new GitCommitsByRepoAndBranch(project, Collections.singletonMap(repository, GitCommitsByBranch.fromSingleBranch(repository, branch, commits)));
  }
}

