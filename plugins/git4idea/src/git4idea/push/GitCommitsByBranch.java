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
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds Git commits made in a single repository grouped by branches.
 *
 * @author Kirill Likhodedov
 */
public class GitCommitsByBranch {

  private final Map<GitBranch, List<GitCommit>> myCommitsByBranch;

  GitCommitsByBranch(Map<GitBranch, List<GitCommit>> commitsByBranch) {
    myCommitsByBranch = commitsByBranch;
  }

  public boolean isEmpty() {
    return myCommitsByBranch.isEmpty();
  }

  public int commitsNumber() {
    int sum = 0;
    for (List<GitCommit> commits : myCommitsByBranch.values()) {
      sum += commits.size();
    }
    return sum;
  }

  public Collection<GitBranch> getBranches() {
    return myCommitsByBranch.keySet();
  }

  public List<GitCommit> get(GitBranch branch) {
    return myCommitsByBranch.get(branch);
  }

  /**
   * Returns new GitCommitsByBranch that contains commits only from the given branch (or nothing, if the given branch didn't exist in
   * the original structure).
   */
  public GitCommitsByBranch retain(@NotNull GitBranch branch) {
    Map<GitBranch, List<GitCommit>> res = new HashMap<GitBranch, List<GitCommit>>();
    if (myCommitsByBranch.containsKey(branch)) {
      res.put(branch, myCommitsByBranch.get(branch));
    }
    return new GitCommitsByBranch(res);
  }
}
