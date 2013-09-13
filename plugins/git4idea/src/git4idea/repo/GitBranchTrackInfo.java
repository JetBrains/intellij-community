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
package git4idea.repo;

import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
public class GitBranchTrackInfo {

  @NotNull private final GitLocalBranch myLocalBranch;
  @NotNull private final GitRemoteBranch myRemoteBranch;
  private final boolean myMerge;

  public GitBranchTrackInfo(@NotNull GitLocalBranch localBranch, @NotNull GitRemoteBranch remoteBranch, boolean merge) {
    myLocalBranch = localBranch;
    myRemoteBranch = remoteBranch;
    myMerge = merge;
  }

  @NotNull
  public GitLocalBranch getLocalBranch() {
    return myLocalBranch;
  }

  @NotNull
  public GitRemote getRemote() {
    return myRemoteBranch.getRemote();
  }

  @NotNull
  public GitRemoteBranch getRemoteBranch() {
    return myRemoteBranch;
  }

  @Override
  public String toString() {
    return String.format("%s->%s", myLocalBranch.getName(), myRemoteBranch.getName());
  }

  @SuppressWarnings("ConstantConditions") // fields may possibly become null in future
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitBranchTrackInfo that = (GitBranchTrackInfo)o;

    if (myMerge != that.myMerge) return false;
    if (myLocalBranch != null ? !myLocalBranch.equals(that.myLocalBranch) : that.myLocalBranch != null) return false;
    if (myRemoteBranch != null ? !myRemoteBranch.equals(that.myRemoteBranch) : that.myRemoteBranch != null) return false;

    return true;
  }

  @SuppressWarnings("ConstantConditions") // fields may possibly become null in future
  @Override
  public int hashCode() {
    int result = myLocalBranch != null ? myLocalBranch.hashCode() : 0;
    result = 31 * result + (myRemoteBranch != null ? myRemoteBranch.hashCode() : 0);
    result = 31 * result + (myMerge ? 1 : 0);
    return result;
  }
}
