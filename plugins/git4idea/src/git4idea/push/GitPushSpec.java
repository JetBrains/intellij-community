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

import com.intellij.openapi.diagnostic.Logger;
import git4idea.GitLocalBranch;
import git4idea.GitLogger;
import git4idea.GitRemoteBranch;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
public class GitPushSpec {

  private static final Logger LOG = GitLogger.PUSH_LOG;

  @NotNull private final GitLocalBranch mySource;
  @Nullable private final GitRemoteBranch myDest;

  @NotNull
  public static GitPushSpec collect(GitRepository repository) {
    repository.update();
    GitLocalBranch currentBranch = repository.getCurrentBranch();
    LOG.assertTrue(currentBranch != null, "Push shouldn't be available in the detached HEAD state");
    return new GitPushSpec(currentBranch, currentBranch.findTrackedBranch(repository));
  }

  GitPushSpec(@NotNull GitLocalBranch source, @Nullable GitRemoteBranch dest) {
    myDest = dest;
    mySource = source;
  }

  @NotNull
  public GitLocalBranch getSource() {
    return mySource;
  }

  /**
   * Returns the destination branch: branch on the remote which the source branch will be pushed to.
   * @return destination branch or null if no destination branch has been defined (i. e. if there is no current branch, and no branch has
   * been defined via the Push dialog).
   */
  @Nullable
  public GitRemoteBranch getDest() {
    return myDest;
  }

  @Override
  public String toString() {
    return mySource + "->" + myDest;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitPushSpec spec = (GitPushSpec)o;

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
