// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
public final class GitLocalBranch extends GitBranch {

  public GitLocalBranch(@NotNull String name) {
    super(name);
  }

  @Override
  public boolean isRemote() {
    return false;
  }

  public @Nullable GitRemoteBranch findTrackedBranch(@NotNull GitRepository repository) {
    GitBranchTrackInfo info = GitBranchUtil.getTrackInfoForBranch(repository, this);
    return info != null ? info.getRemoteBranch() : null;
  }

  @Override
  public int compareTo(GitReference o) {
    if (o instanceof GitLocalBranch) {
      // optimization: do not build getFullName
      return StringUtil.compare(myName, o.myName, SystemInfo.isFileSystemCaseSensitive);
    }
    return super.compareTo(o);
  }
}
