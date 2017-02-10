/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.dvcs.repo.Repository;
import com.intellij.vcs.log.Hash;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class GitRepoInfo {

  @Nullable private final GitLocalBranch myCurrentBranch;
  @Nullable private final String myCurrentRevision;
  @NotNull private final Repository.State myState;
  @NotNull private final Set<GitRemote> myRemotes;
  @NotNull private final Map<GitLocalBranch, Hash> myLocalBranches;
  @NotNull private final Map<GitRemoteBranch, Hash> myRemoteBranches;
  @NotNull private final Set<GitBranchTrackInfo> myBranchTrackInfos;

  public GitRepoInfo(@Nullable GitLocalBranch currentBranch,
                     @Nullable String currentRevision,
                     @NotNull Repository.State state,
                     @NotNull Collection<GitRemote> remotes,
                     @NotNull Map<GitLocalBranch, Hash> localBranches,
                     @NotNull Map<GitRemoteBranch, Hash> remoteBranches,
                     @NotNull Collection<GitBranchTrackInfo> branchTrackInfos) {
    myCurrentBranch = currentBranch;
    myCurrentRevision = currentRevision;
    myState = state;
    myRemotes = new LinkedHashSet<>(remotes);
    myLocalBranches = new LinkedHashMap<>(localBranches);
    myRemoteBranches = new LinkedHashMap<>(remoteBranches);
    myBranchTrackInfos = new LinkedHashSet<>(branchTrackInfos);
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

  @Nullable
  public String getCurrentRevision() {
    return myCurrentRevision;
  }

  @NotNull
  public Repository.State getState() {
    return myState;
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
    if (!areEqual(myLocalBranches, info.myLocalBranches)) return false;
    if (!areEqual(myRemoteBranches, info.myRemoteBranches)) return false;

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
    return result;
  }

  @Override
  public String toString() {
    return String
      .format("GitRepoInfo{current=%s, remotes=%s, localBranches=%s, remoteBranches=%s, trackInfos=%s}", myCurrentBranch, myRemotes,
              myLocalBranches, myRemoteBranches, myBranchTrackInfos);
  }

  private static <T extends GitBranch> boolean areEqual(Map<T, Hash> c1, Map<T, Hash> c2) {
    // GitBranch has perverted equals contract (see the comment there)
    // until GitBranch is created only from a single place with correctly defined Hash, we can't change its equals
    THashSet<Map.Entry<? extends GitBranch, Hash>> set1 =
      new THashSet<>(c1.entrySet(), new BranchesComparingStrategy());
    THashSet<Map.Entry<? extends GitBranch, Hash>> set2 =
      new THashSet<>(c2.entrySet(), new BranchesComparingStrategy());
    return set1.equals(set2);
  }

  private static class BranchesComparingStrategy implements TObjectHashingStrategy<Map.Entry<? extends GitBranch, Hash>> {

    @Override
    public int computeHashCode(@NotNull Map.Entry<? extends GitBranch, Hash> branchEntry) {
      return 31 * branchEntry.getKey().getName().hashCode() + branchEntry.getValue().hashCode();
    }

    @Override
    public boolean equals(@NotNull Map.Entry<? extends GitBranch, Hash> b1, @NotNull Map.Entry<? extends GitBranch, Hash> b2) {
      if (b1 == b2) {
        return true;
      }
      if (b1.getClass() != b2.getClass()) {
        return false;
      }
      return b1.getKey().getName().equals(b2.getKey().getName()) && b2.getValue().equals(b2.getValue());
    }
  }

}
