// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo;

import com.intellij.dvcs.repo.Repository;
import com.intellij.vcs.log.Hash;
import git4idea.GitLocalBranch;
import git4idea.GitReference;
import git4idea.GitRemoteBranch;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GitRepoInfo {

  @Nullable private final GitLocalBranch myCurrentBranch;
  @Nullable private final String myCurrentRevision;
  @NotNull private final Repository.State myState;
  @NotNull private final Set<GitRemote> myRemotes;
  @NotNull private final Map<GitLocalBranch, Hash> myLocalBranches;
  @NotNull private final Map<GitRemoteBranch, Hash> myRemoteBranches;
  @NotNull private final Set<GitBranchTrackInfo> myBranchTrackInfos;
  @NotNull private final Map<String, GitBranchTrackInfo> myBranchTrackInfosMap;
  @NotNull private final Collection<GitSubmoduleInfo> mySubmodules;
  @NotNull private final GitHooksInfo myHooksInfo;
  private final boolean myIsShallow;

  public GitRepoInfo(@Nullable GitLocalBranch currentBranch,
                     @Nullable String currentRevision,
                     @NotNull Repository.State state,
                     @NotNull Collection<GitRemote> remotes,
                     @NotNull Map<GitLocalBranch, Hash> localBranches,
                     @NotNull Map<GitRemoteBranch, Hash> remoteBranches,
                     @NotNull Collection<GitBranchTrackInfo> branchTrackInfos,
                     @NotNull Collection<GitSubmoduleInfo> submodules,
                     @NotNull GitHooksInfo hooksInfo,
                     boolean isShallow) {
    myCurrentBranch = currentBranch;
    myCurrentRevision = currentRevision;
    myState = state;
    myRemotes = new LinkedHashSet<>(remotes);
    myLocalBranches = new HashMap<>(localBranches);
    myRemoteBranches = new HashMap<>(remoteBranches);
    myBranchTrackInfos = new LinkedHashSet<>(branchTrackInfos);
    mySubmodules = submodules;
    myHooksInfo = hooksInfo;
    myIsShallow = isShallow;

    myBranchTrackInfosMap = new THashMap<>(GitReference.BRANCH_NAME_HASHING_STRATEGY);
    for (GitBranchTrackInfo info : branchTrackInfos) {
      myBranchTrackInfosMap.put(info.getLocalBranch().getName(), info);
    }
  }

  @Nullable
  public GitLocalBranch getCurrentBranch() {
    return myCurrentBranch;
  }

  @NotNull
  public Collection<GitRemote> getRemotes() {
    return myRemotes;
  }

  @NotNull
  public Map<GitLocalBranch, Hash> getLocalBranchesWithHashes() {
    return myLocalBranches;
  }

  @NotNull
  public Map<GitRemoteBranch, Hash> getRemoteBranchesWithHashes() {
    return myRemoteBranches;
  }

  @NotNull
  @Deprecated
  public Collection<GitRemoteBranch> getRemoteBranches() {
    return myRemoteBranches.keySet();
  }

  @NotNull
  public Collection<GitBranchTrackInfo> getBranchTrackInfos() {
    return myBranchTrackInfos;
  }

  @NotNull
  public Map<String, GitBranchTrackInfo> getBranchTrackInfosMap() {
    return myBranchTrackInfosMap;
  }

  @Nullable
  public String getCurrentRevision() {
    return myCurrentRevision;
  }

  @NotNull
  public Repository.State getState() {
    return myState;
  }

  @NotNull
  public Collection<GitSubmoduleInfo> getSubmodules() {
    return mySubmodules;
  }

  @NotNull
  public GitHooksInfo getHooksInfo() {
    return myHooksInfo;
  }

  public boolean isShallow() {
    return myIsShallow;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitRepoInfo info = (GitRepoInfo)o;

    if (myState != info.myState) return false;
    if (myCurrentRevision != null ? !myCurrentRevision.equals(info.myCurrentRevision) : info.myCurrentRevision != null) return false;
    if (myCurrentBranch != null ? !myCurrentBranch.equals(info.myCurrentBranch) : info.myCurrentBranch != null) return false;
    if (!myRemotes.equals(info.myRemotes)) return false;
    if (!myBranchTrackInfos.equals(info.myBranchTrackInfos)) return false;
    if (!myLocalBranches.equals(info.myLocalBranches)) return false;
    if (!myRemoteBranches.equals(info.myRemoteBranches)) return false;
    if (!mySubmodules.equals(info.mySubmodules)) return false;
    if (!myHooksInfo.equals(info.myHooksInfo)) return false;
    if (myIsShallow != info.myIsShallow) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myCurrentBranch != null ? myCurrentBranch.hashCode() : 0;
    result = 31 * result + (myCurrentRevision != null ? myCurrentRevision.hashCode() : 0);
    result = 31 * result + myState.hashCode();
    result = 31 * result + myRemotes.hashCode();
    result = 31 * result + myLocalBranches.hashCode();
    result = 31 * result + myRemoteBranches.hashCode();
    result = 31 * result + myBranchTrackInfos.hashCode();
    result = 31 * result + mySubmodules.hashCode();
    result = 31 * result + myHooksInfo.hashCode();
    result = 31 * result + (myIsShallow ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return String.format("GitRepoInfo{current=%s, remotes=%s, localBranches=%s, remoteBranches=%s, trackInfos=%s, submodules=%s, hooks=%s}",
                         myCurrentBranch, myRemotes, myLocalBranches, myRemoteBranches, myBranchTrackInfos, mySubmodules, myHooksInfo);
  }
}
