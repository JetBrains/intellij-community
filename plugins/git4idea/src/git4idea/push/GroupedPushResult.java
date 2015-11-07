/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.util.containers.ContainerUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

class GroupedPushResult {

  @NotNull final Map<GitRepository, GitPushRepoResult> successful;
  @NotNull final Map<GitRepository, GitPushRepoResult> errors;
  @NotNull final Map<GitRepository, GitPushRepoResult> rejected;
  @NotNull final Map<GitRepository, GitPushRepoResult> customRejected;

  private GroupedPushResult(@NotNull Map<GitRepository, GitPushRepoResult> successful,
                            @NotNull Map<GitRepository, GitPushRepoResult> errors,
                            @NotNull Map<GitRepository, GitPushRepoResult> rejected,
                            @NotNull Map<GitRepository, GitPushRepoResult> customRejected) {
    this.successful = successful;
    this.errors = errors;
    this.rejected = rejected;
    this.customRejected = customRejected;
  }

  @NotNull
  static GroupedPushResult group(@NotNull Map<GitRepository, GitPushRepoResult> results) {
    Map<GitRepository, GitPushRepoResult> successful = ContainerUtil.newHashMap();
    Map<GitRepository, GitPushRepoResult> rejected = ContainerUtil.newHashMap();
    Map<GitRepository, GitPushRepoResult> customRejected = ContainerUtil.newHashMap();
    Map<GitRepository, GitPushRepoResult> errors = ContainerUtil.newHashMap();
    for (Map.Entry<GitRepository, GitPushRepoResult> entry : results.entrySet()) {
      GitRepository repository = entry.getKey();
      GitPushRepoResult result = entry.getValue();

      if (result.getType() == GitPushRepoResult.Type.REJECTED_NO_FF) {
        rejected.put(repository, result);
      }
      else if (result.getType() == GitPushRepoResult.Type.ERROR) {
        errors.put(repository, result);
      }
      else if (result.getType() == GitPushRepoResult.Type.REJECTED_OTHER) {
        customRejected.put(repository, result);
      }
      else {
        successful.put(repository, result);
      }
    }
    return new GroupedPushResult(successful, errors, rejected, customRejected);
  }
}
