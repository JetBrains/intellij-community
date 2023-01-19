/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

  @Nullable
  public GitRemoteBranch findTrackedBranch(@NotNull GitRepository repository) {
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
