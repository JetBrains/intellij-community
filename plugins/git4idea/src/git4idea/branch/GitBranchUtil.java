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
package git4idea.branch;

import git4idea.GitBranch;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
public class GitBranchUtil {

  private GitBranchUtil() {}

  /**
   * Returns the tracking information about the given branch in the given repository,
   * or null if there is no such information (i.e. if the branch doesn't have a tracking branch).
   */
  @Nullable
  public static GitBranchTrackInfo getTrackInfoForBranch(@NotNull GitRepository repository, @NotNull GitBranch branch) {
    for (GitBranchTrackInfo trackInfo : repository.getConfig().getBranchTrackInfos()) {
      if (trackInfo.getBranch().equals(branch.getName())) {
        return trackInfo;
      }
    }
    return null;
  }
}
