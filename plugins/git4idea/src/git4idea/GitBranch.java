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
import com.intellij.vcs.log.Hash;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>Represents a Git branch, local or remote.</p>
 *
 * <p>Local and remote branches are different in that nature, that remote branch has complex name ("origin/master") containing both
 *    the name of the remote and the name of the branch. And while the standard name of the remote branch if origin/master,
 *    in certain cases we must operate with the name which the branch has at the remote: "master".</p>
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
public abstract class GitBranch extends GitReference {

  @NonNls public static final String REFS_HEADS_PREFIX = "refs/heads/"; // Prefix for local branches ({@value})
  @NonNls public static final String REFS_REMOTES_PREFIX = "refs/remotes/"; // Prefix for remote branches ({@value})

  private static final Logger LOG = Logger.getInstance(GitBranch.class);

  /**
   * @deprecated All usages should be reviewed and substituted with actual GitBranch objects with Hashes retrieved from the GitRepository.
   */
  @Deprecated
  @Nullable
  public static final Hash DUMMY_HASH = null;

  protected GitBranch(@NotNull String name) {
    super(GitBranchUtil.stripRefsPrefix(name));
  }

  /**
   * @return true if the branch is remote
   */
  public abstract boolean isRemote();

  @NotNull
  public String getFullName() {
    return (isRemote() ? REFS_REMOTES_PREFIX : REFS_HEADS_PREFIX) + myName;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }

    // Reusing equals from super: only the name is important:
    // branches are considered equal even if they point to different commits.

    /*
      Commenting this for a while, because GitRepository has different update() methods: thus in certain cases only current branch is
      updated => this branch in the branches collection has different hash.
      Need either to update everything always, either to have a GitBranches collection in GitRepository and not recreate it.

    // But if equal branches point to different commits (or have different local/remote nature), then it is a programmer bug:
    // one if GitBranch instances in the calling code is out-of-date.
    // throwing assertion in that case forcing the programmer to update before comparing.
    GitBranch that = (GitBranch)o;
    if (!myHash.equals(that.myHash)) {
      LOG.error("Branches have equal names, but different hash codes. This: " + toLogString() + ", that: " + that.toLogString());
    }
    else if (isRemote() != that.isRemote()) {
      LOG.error("Branches have equal names, but different local/remote type. This: " + toLogString() + ", that: " + that.toLogString());
    }
    */

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
    return String.format("%s:%s", getFullName(), isRemote() ? "remote" : "local");
  }

}
