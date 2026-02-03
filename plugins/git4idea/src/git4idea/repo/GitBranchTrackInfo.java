// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo;

import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
public class GitBranchTrackInfo {

  private final @NotNull GitLocalBranch myLocalBranch;
  private final @NotNull GitRemoteBranch myRemoteBranch;
  private final boolean myMerge;

  public GitBranchTrackInfo(@NotNull GitLocalBranch localBranch, @NotNull GitRemoteBranch remoteBranch, boolean merge) {
    myLocalBranch = localBranch;
    myRemoteBranch = remoteBranch;
    myMerge = merge;
  }

  public @NotNull GitLocalBranch getLocalBranch() {
    return myLocalBranch;
  }

  public @NotNull GitRemote getRemote() {
    return myRemoteBranch.getRemote();
  }

  public @NotNull GitRemoteBranch getRemoteBranch() {
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
