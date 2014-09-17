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

import java.util.Map;

class GroupedPushResult {

  final Map<GitRepository, GitPushRepoResult> successful;
  final Map<GitRepository, GitPushRepoResult> errors;
  final Map<GitRepository, GitPushRepoResult> rejected;

  private GroupedPushResult(Map<GitRepository, GitPushRepoResult> successful,
                            Map<GitRepository, GitPushRepoResult> errors,
                            Map<GitRepository, GitPushRepoResult> rejected) {
    this.successful = successful;
    this.errors = errors;
    this.rejected = rejected;
  }

  static GroupedPushResult group(Map<GitRepository, GitPushRepoResult> results) {
    Map<GitRepository, GitPushRepoResult> successful = ContainerUtil.newHashMap();
    Map<GitRepository, GitPushRepoResult> rejected = ContainerUtil.newHashMap();
    Map<GitRepository, GitPushRepoResult> errors = ContainerUtil.newHashMap();
    for (Map.Entry<GitRepository, GitPushRepoResult> entry : results.entrySet()) {
      GitRepository repository = entry.getKey();
      GitPushRepoResult result = entry.getValue();

      if (result.getType() == GitPushRepoResult.Type.REJECTED) {
        rejected.put(repository, result);
      }
      else if (result.getType() == GitPushRepoResult.Type.ERROR) {
        errors.put(repository, result);
      }
      else {
        successful.put(repository, result);
      }
    }
    return new GroupedPushResult(successful, errors, rejected);
  }
}
