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

import git4idea.GitBranch;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
public class GitBranchTrackInfo {

  @NotNull private final String myBranch;
  @NotNull private final GitRemote myRemote;
  @NotNull private final String myRemoteBranch;
  private final boolean myMerge;

  GitBranchTrackInfo(@NotNull String branch, @NotNull GitRemote remote, @NotNull String remoteBranch, boolean merge) {
    myBranch = branch;
    myMerge = merge;
    if (remoteBranch.startsWith(GitBranch.REFS_HEADS_PREFIX)) {
      myRemoteBranch = remoteBranch.substring(GitBranch.REFS_HEADS_PREFIX.length());
    }
    else {
      myRemoteBranch = remoteBranch;
    }
    myRemote = remote;
  }

  @NotNull
  public String getBranch() {
    return myBranch;
  }

  @NotNull
  public GitRemote getRemote() {
    return myRemote;
  }

  @NotNull
  public String getRemoteBranch() {
    return myRemoteBranch;
  }

  public boolean isMerge() {
    return myMerge;
  }

  @Override
  public String toString() {
    return String.format("%s %s %s %b", myBranch, myRemote, myRemoteBranch, myMerge);
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public boolean equals(Object o) {

    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitBranchTrackInfo that = (GitBranchTrackInfo)o;

    if (myMerge != that.myMerge) return false;
    if (myBranch != null ? !myBranch.equals(that.myBranch) : that.myBranch != null) return false;
    if (myRemote != null ? !myRemote.equals(that.myRemote) : that.myRemote != null) return false;
    if (myRemoteBranch != null ? !myRemoteBranch.equals(that.myRemoteBranch) : that.myRemoteBranch != null) return false;

    return true;
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public int hashCode() {
    int result = myBranch != null ? myBranch.hashCode() : 0;
    result = 31 * result + (myRemote != null ? myRemote.hashCode() : 0);
    result = 31 * result + (myRemoteBranch != null ? myRemoteBranch.hashCode() : 0);
    result = 31 * result + (myMerge ? 1 : 0);
    return result;
  }
}
