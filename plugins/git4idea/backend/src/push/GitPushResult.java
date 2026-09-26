// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push;

import com.intellij.history.Label;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import git4idea.repo.GitRepository;
import git4idea.update.HashRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Map;

/**
 * Combined push result for all affected repositories in the project.
 */
public final class GitPushResult {
  private final @NotNull Map<GitRepository, GitPushRepoResult> myResults;
  private final @NotNull UpdatedFiles myUpdatedFiles;
  private final @Nullable Label myBeforeUpdateLabel;
  private final @Nullable Label myAfterUpdateLabel;
  private final @NotNull Map<GitRepository, HashRange> myUpdatedRanges;

  @VisibleForTesting
  public GitPushResult(@NotNull Map<GitRepository, GitPushRepoResult> results,
                       @NotNull UpdatedFiles files,
                       @Nullable Label beforeUpdateLabel,
                       @Nullable Label afterUpdateLabel,
                       @NotNull Map<GitRepository, HashRange> ranges) {
    myResults = results;
    myUpdatedFiles = files;
    myBeforeUpdateLabel = beforeUpdateLabel;
    myAfterUpdateLabel = afterUpdateLabel;
    myUpdatedRanges = ranges;
  }

  public @NotNull Map<GitRepository, GitPushRepoResult> getResults() {
    return myResults;
  }

  public @NotNull UpdatedFiles getUpdatedFiles() {
    return myUpdatedFiles;
  }

  public @Nullable Label getBeforeUpdateLabel() {
    return myBeforeUpdateLabel;
  }

  public @Nullable Label getAfterUpdateLabel() {
    return myAfterUpdateLabel;
  }

  public @NotNull Map<GitRepository, HashRange> getUpdatedRanges() {
    return myUpdatedRanges;
  }
}
