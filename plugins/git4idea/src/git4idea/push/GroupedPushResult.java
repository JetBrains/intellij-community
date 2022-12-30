// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push;

import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

final class GroupedPushResult {

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
    Map<GitRepository, GitPushRepoResult> successful = new HashMap<>();
    Map<GitRepository, GitPushRepoResult> rejected = new HashMap<>();
    Map<GitRepository, GitPushRepoResult> customRejected = new HashMap<>();
    Map<GitRepository, GitPushRepoResult> errors = new HashMap<>();
    for (Map.Entry<GitRepository, GitPushRepoResult> entry : results.entrySet()) {
      GitRepository repository = entry.getKey();
      GitPushRepoResult result = entry.getValue();

      Map<GitRepository, GitPushRepoResult> map = switch (result.getType()) {
        case REJECTED_NO_FF -> rejected;
        case ERROR -> errors;
        case REJECTED_STALE_INFO, REJECTED_OTHER -> customRejected;
        default -> successful;
      };
      map.put(repository, result);
    }
    return new GroupedPushResult(successful, errors, rejected, customRejected);
  }
}
