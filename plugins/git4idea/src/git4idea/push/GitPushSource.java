// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.PushSource;
import git4idea.GitLocalBranch;
import org.jetbrains.annotations.NotNull;

public abstract class GitPushSource implements PushSource {

  @NotNull
  public static GitPushSource create(@NotNull GitLocalBranch branch) {
    return new OnBranch(branch);
  }

  @NotNull
  public static GitPushSource create(@NotNull String revision) {
    return new DetachedHead(revision);
  }

  @NotNull
  public abstract GitLocalBranch getBranch();

  @Override
  public String toString() {
    return getPresentation();
  }

  static final class OnBranch extends GitPushSource {
    @NotNull private final GitLocalBranch myBranch;

    private OnBranch(@NotNull GitLocalBranch branch) {
      myBranch = branch;
    }

    @NotNull
    @Override
    public String getPresentation() {
      return myBranch.getName();
    }

    @NotNull
    @Override
    public GitLocalBranch getBranch() {
      return myBranch;
    }
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
  }
}
