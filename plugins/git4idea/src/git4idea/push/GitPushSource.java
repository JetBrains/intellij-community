// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.PushSource;
import com.intellij.openapi.util.NlsSafe;
import git4idea.GitLocalBranch;
import git4idea.GitTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GitPushSource implements PushSource {

  public static @NotNull GitPushSource create(@NotNull GitLocalBranch branch) {
    return new OnBranch(branch);
  }

  /**
   * Create information to push from.
   * All commits before this including this one will be pushed to a target branch.
   */
  public static @NotNull GitPushSource createRef(@NotNull GitLocalBranch branch, @NotNull String revision) {
    return new OnRevision(branch, revision);
  }

  public static @NotNull GitPushSource createDetached(@NotNull String revision) {
    return new DetachedHead(revision);
  }

  public static @NotNull GitPushSource createTag(@NotNull GitTag tag) {
    return new Tag(tag);
  }

  public abstract @Nullable GitLocalBranch getBranch();

  public abstract @NotNull String getRevision();

  public abstract boolean isBranchRef();

  @Override
  public String toString() {
    return getPresentation();
  }

  static final class OnBranch extends GitPushSource {
    private final @NotNull GitLocalBranch myBranch;

    private OnBranch(@NotNull GitLocalBranch branch) {
      myBranch = branch;
    }

    @Override
    public @NotNull String getPresentation() {
      return myBranch.getName();
    }

    @Override
    public @NotNull GitLocalBranch getBranch() {
      return myBranch;
    }

    @Override
    public @NotNull String getRevision() {
      return myBranch.getFullName();
    }

    @Override
    public boolean isBranchRef() {
      return true;
    }
  }

  static final class OnRevision extends GitPushSource {
    private final @NotNull GitLocalBranch myBranch;
    private final @NlsSafe String myRevision;

    private OnRevision(@NotNull GitLocalBranch branch, @NotNull String revision) {
      myBranch = branch;
      myRevision = revision;
    }

    @Override
    public @NotNull String getPresentation() {
      return DvcsUtil.getShortHash(myRevision);
    }

    @Override
    public @NotNull GitLocalBranch getBranch() {
      return myBranch;
    }

    @Override
    public @NotNull String getRevision() {
      return myRevision;
    }

    @Override
    public boolean isBranchRef() {
      return false;
    }
  }

  static class DetachedHead extends GitPushSource {
    private final @NotNull String myRevision;

    DetachedHead(@NotNull String revision) {
      myRevision = revision;
    }

    @Override
    public @NotNull String getPresentation() {
      return DvcsUtil.getShortHash(myRevision);
    }

    @Override
    public @Nullable GitLocalBranch getBranch() {
      return null;
    }

    @Override
    public @NotNull String getRevision() {
      return myRevision;
    }

    @Override
    public boolean isBranchRef() {
      return false;
    }
  }

  @ApiStatus.Internal
  public static final class Tag extends GitPushSource {
    private final @NotNull GitTag tag;

    public Tag(@NotNull GitTag tag) {
      this.tag = tag;
    }

    @Override
    public @NotNull String getPresentation() {
      return tag.getName();
    }

    @Override
    public @Nullable GitLocalBranch getBranch() {
      return null;
    }

    @Override
    public @NotNull String getRevision() {
      return tag.getFullName();
    }

    @Override
    public boolean isBranchRef() {
      return false;
    }

    @NotNull
    GitTag getTag() {
      return tag;
    }
  }
}
