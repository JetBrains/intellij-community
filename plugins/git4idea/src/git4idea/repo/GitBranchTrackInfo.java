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

import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
public class GitBranchTrackInfo {

  private final String myBranch;
  private final GitRemote myRemote;
  private final String myRemoteBranch;
  private final boolean myMerge;

  GitBranchTrackInfo(@NotNull String branch, @NotNull GitRemote remote, @NotNull String remoteBranch, boolean merge) {
    myBranch = branch;
    myMerge = merge;
    myRemoteBranch = remoteBranch;
    myRemote = remote;
  }

  @Override
  public String toString() {
    return String.format("%s %s %s %b", myBranch, myRemote, myRemoteBranch, myMerge);
  }

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

  @Override
  public int hashCode() {
    int result = myBranch != null ? myBranch.hashCode() : 0;
    result = 31 * result + (myRemote != null ? myRemote.hashCode() : 0);
    result = 31 * result + (myRemoteBranch != null ? myRemoteBranch.hashCode() : 0);
    result = 31 * result + (myMerge ? 1 : 0);
    return result;
  }
}
