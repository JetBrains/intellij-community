// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRemote;
import org.jetbrains.annotations.NotNull;

public final class GitStandardRemoteBranch extends GitRemoteBranch {

  private final @NotNull GitRemote myRemote;
  private final @NotNull String myNameAtRemote;

  public GitStandardRemoteBranch(@NotNull GitRemote remote, @NotNull String nameAtRemote) {
    super(formStandardName(remote, GitBranchUtil.stripRefsPrefix(nameAtRemote)));
    myRemote = remote;
    myNameAtRemote = GitBranchUtil.stripRefsPrefix(nameAtRemote);
  }

  private static @NotNull String formStandardName(@NotNull GitRemote remote, @NotNull String nameAtRemote) {
    return remote.getName() + "/" + nameAtRemote;
  }

  @Override
  public @NotNull GitRemote getRemote() {
    return myRemote;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    GitStandardRemoteBranch branch = (GitStandardRemoteBranch)o;

    if (!myNameAtRemote.equals(branch.myNameAtRemote)) return false;
    if (!myRemote.equals(branch.myRemote)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myRemote.hashCode();
    result = 31 * result + myNameAtRemote.hashCode();
    return result;
  }

  @Override
  public @NotNull String getNameForRemoteOperations() {
    return myNameAtRemote;
  }

  @Override
  public @NotNull String getNameForLocalOperations() {
    return myName;
  }

  @Override
  public int compareTo(GitReference o) {
    if (o instanceof GitStandardRemoteBranch) {
      // optimization: do not build getFullName
      return StringUtil.compare(myName, o.myName, SystemInfo.isFileSystemCaseSensitive);
    }
    return super.compareTo(o);
  }
}
