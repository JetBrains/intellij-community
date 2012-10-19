/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static git4idea.repo.GitRepository.*;

/**
 * <p>Represents a Git branch, local or remote.</p>
 *
 * <p>It contains information about the branch name and the hash it points to.
 *    Note that the object (including the hash) is immutable. That means that if branch reference move along, you have to get new instance
 *    of the GitBranch object, probably from {@link GitRepository#getBranches()} or {@link git4idea.repo.GitRepository#getCurrentBranch()}.
 * </p>
 *
 * <p>GitBranches are equal, if their full names are equal. That means that if two GitBranch objects have different hashes, they
 *    are considered equal. But in this case an error if logged, becase it means that one of this GitBranch instances is out-of-date, and
 *    it is required to use an {@link GitRepository#update(TrackedTopic...) updated} version.</p>
 */
public class GitBranch extends GitReference {

  @NonNls public static final String REFS_HEADS_PREFIX = "refs/heads/"; // Prefix for local branches ({@value})
  @NonNls public static final String REFS_REMOTES_PREFIX = "refs/remotes/"; // Prefix for remote branches ({@value})

  /**
   * @deprecated All usages should be reviewed and substituted with actual GitBranch objects with Hashes retrieved from the GitRepository.
   */
  @Deprecated
  public static final Hash DUMMY_HASH = Hash.create("");

  private static final Logger LOG = Logger.getInstance(GitBranch.class);

  @NotNull private final Hash myHash;
  private final boolean myRemote;

  public GitBranch(@NotNull String name, @NotNull Hash hash, boolean remote) {
    super(name);
    myRemote = remote;
    myHash = hash;
  }
  
  /**
   * <p>Returns the hash on which this branch is reference to.</p>
   *
   * <p>In certain cases (which are to be eliminated in the future) it may be empty,
   *    if this information wasn't supplied to the GitBranch constructor.</p>
   */
  @NotNull
  public String getHash() {
    return myHash.asString();
  }

  /**
   * @return true if the branch is remote
   */
  public boolean isRemote() {
    return myRemote;
  }

  @NotNull
  public String getFullName() {
    return (myRemote ? REFS_REMOTES_PREFIX : REFS_HEADS_PREFIX) + myName;
  }

  /**
   * <p>
   *   Returns the "local" name of a remote branch.
   *   For example, for "origin/master" returns "master".
   * </p>
   * <p>
   *   Note that slashes are not permitted in remote names, so if we know that a branch is a remote branch,
   *   we know that local branch name is tha part after the slash.
   * </p>
   * @return "local" name of a remote branch, or just {@link #getName()} for local branches.
   */
  @NotNull
  public String getShortName() {
    return splitNameOfRemoteBranch(getName()).getSecond();
  }

  /**
   * Returns the remote and the "local" name of a remote branch.
   * Expects branch in format "origin/master", i.e. remote/branch
   */
  public static Pair<String, String> splitNameOfRemoteBranch(String branchName) {
    int firstSlash = branchName.indexOf('/');
    String remoteName = firstSlash > -1 ? branchName.substring(0, firstSlash) : branchName;
    String remoteBranchName = branchName.substring(firstSlash + 1);
    return Pair.create(remoteName, remoteBranchName);
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }

    // Reusing equals from super: only the name is important:
    // branches are considered equal even if they point to different commits.
    // But if equal branches point to different commits (or have different local/remote nature), then it is a programmer bug:
    // one if GitBranch instances in the calling code is out-of-date.
    // throwing assertion in that case forcing the programmer to update before comparing.
    GitBranch that = (GitBranch)o;
    if (!myHash.equals(that.myHash)) {
      LOG.error("Branches have equal names, but different hash codes. This: " + toLogString() + ", that: " + that.toLogString());
    }
    else if (myRemote != that.myRemote) {
      LOG.error("Branches have equal names, but different local/remote type. This: " + toLogString() + ", that: " + that.toLogString());
    }

    return true;
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public String toString() {
    return super.toString();
  }

  @NotNull
  public String toLogString() {
    return String.format("%s:%s:%s", getFullName(), getHash(), isRemote() ? "remote" : "local");
  }

}
