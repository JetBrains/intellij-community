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
package git4idea.branch;

import com.intellij.openapi.diagnostic.Logger;
import git4idea.GitLocalBranch;
import git4idea.GitLogger;
import git4idea.GitRemoteBranch;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holder for a Git branch and the branch it is "connected" with.
 * Usually it is the tracked branch, but may any other (for example, when pushing to an alternative branch).
 *
 * @author Kirill Likhodedov
 */
public class GitBranchPair {

  private static final Logger LOG = GitLogger.CORE_LOG;

  @NotNull private GitLocalBranch mySource;
  @Nullable private GitRemoteBranch myDest;

  public GitBranchPair(@NotNull GitLocalBranch source, @Nullable GitRemoteBranch destination) {
    mySource = source;
    myDest = destination;
  }

  @NotNull
  public static GitBranchPair findCurrentAnTracked(@NotNull GitRepository repository) {
    repository.update();
    GitLocalBranch currentBranch = repository.getCurrentBranch();
    LOG.assertTrue(currentBranch != null, "Push shouldn't be available in the detached HEAD state");
    return new GitBranchPair(currentBranch, currentBranch.findTrackedBranch(repository));
  }

  @NotNull
  public GitLocalBranch getSource() {
    return mySource;
  }

  @Nullable
  public GitRemoteBranch getDest() {
    return myDest;
  }

  @Override
  public String toString() {
    String dest = myDest == null ? "nowhere" : myDest.getFullName();
    return mySource.getName() + "->" + dest;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitBranchPair spec = (GitBranchPair)o;

    if (myDest != null ? !myDest.equals(spec.myDest) : spec.myDest != null) return false;
    if (!mySource.equals(spec.mySource)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = mySource.hashCode();
    result = 31 * result + (myDest != null ? myDest.hashCode() : 0);
    return result;
  }

}
