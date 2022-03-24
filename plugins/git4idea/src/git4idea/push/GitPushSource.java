// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.PushSource;
import com.intellij.openapi.util.NlsSafe;
import git4idea.GitLocalBranch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GitPushSource implements PushSource {

  @NotNull
  public static GitPushSource create(@NotNull GitLocalBranch branch) {
    return new OnBranch(branch, null);
  }

  /**
   * Create information to push from.
   * If the revision is not null then all commits before this including this one will be pushed to a target branch.
   * Otherwise, an entire local branch will be pushed to the target.
   * For a new source branch and revision == null related upstream will be set.
   */
  @NotNull
  public static GitPushSource create(@NotNull GitLocalBranch branch, @Nullable String revision) {
    return new OnBranch(branch, revision);
  }

  @NotNull
  public static GitPushSource create(@NotNull String revision) {
    return new DetachedHead(revision);
  }

  @NotNull
  public abstract GitLocalBranch getBranch();

  @NotNull
  public abstract String getRevision();

  @Override
  public String toString() {
    return getPresentation();
  }

  static final class OnBranch extends GitPushSource {
    @NotNull private final GitLocalBranch myBranch;
    @NlsSafe private final String myRevision;
    private final boolean myIsHead;

    private OnBranch(@NotNull GitLocalBranch branch, @Nullable String revision) {
      myBranch = branch;
      myIsHead = revision == null;
      myRevision = myIsHead ? branch.getFullName() : revision;
    }

    @NotNull
    @Override
    public String getPresentation() {
      return myIsHead ? myBranch.getName() : DvcsUtil.getShortHash(myRevision);
    }

    @NotNull
    @Override
    public GitLocalBranch getBranch() {
      return myBranch;
    }

    @NotNull
    @Override
    public String getRevision() { return myRevision; }
  }

  static class DetachedHead extends GitPushSource {
    @NotNull private final String myRevision;

    DetachedHead(@NotNull String revision) {
      myRevision = revision;
    }

    @NotNull
    @Override
    public String getPresentation() {
      return DvcsUtil.getShortHash(myRevision);
    }

    @NotNull
    @Override
    public GitLocalBranch getBranch() {
      throw new IllegalStateException("Push is not allowed from detached HEAD");
    }

    @NotNull
    @Override
    public String getRevision() {
      return myRevision;
    }
  }
}
