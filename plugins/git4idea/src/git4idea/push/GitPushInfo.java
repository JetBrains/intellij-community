/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.push;

import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Kirill Likhodedov
 */
public final class GitPushInfo {

  @NotNull private final Map<GitRepository, Integer> myRepositories;
  @NotNull private final GitPushSpec myPushSpec;

  public GitPushInfo(@NotNull Map<GitRepository, Integer> repositoriesAndNumberOfCommmits, @NotNull GitPushSpec pushSpec) {
    myRepositories = repositoriesAndNumberOfCommmits;
    myPushSpec = pushSpec;
  }

  @NotNull
  public Map<GitRepository, Integer> getRepositoriesWithPushCommitCount() {
    return myRepositories;
  }

  @NotNull
  public GitPushSpec getPushSpec() {
    return myPushSpec;
  }
}
