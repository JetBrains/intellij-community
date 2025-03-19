// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

public final class VcsLogProperties {
  public static final class VcsLogProperty<T> {
    private final @NotNull T defaultValue;

    private VcsLogProperty(@NotNull T defaultValue) {
      this.defaultValue = defaultValue;
    }

    public @NotNull T getOrDefault(VcsLogProvider provider) {
      T value = provider.getPropertyValue(this);
      return value == null ? defaultValue : value;
    }
  }

  public static final @NotNull VcsLogProperty<Boolean> LIGHTWEIGHT_BRANCHES = new VcsLogProperty<>(false);
  public static final @NotNull VcsLogProperty<Boolean> SUPPORTS_INDEXING = new VcsLogProperty<>(false);
  public static final @NotNull VcsLogProperty<Boolean> SUPPORTS_LOG_DIRECTORY_HISTORY = new VcsLogProperty<>(false);
  public static final @NotNull VcsLogProperty<Boolean> CASE_INSENSITIVE_REGEX = new VcsLogProperty<>(true);
  /**
   * True if VCS has separate committer and committed date information which may differ from author and author date
   */
  public static final @NotNull VcsLogProperty<Boolean> HAS_COMMITTER = new VcsLogProperty<>(false);
  /**
   * True if VCS allows incrementally refresh commits in the log. False if full refresh should be performed.
   */
  public static final @NotNull VcsLogProperty<Boolean> SUPPORTS_INCREMENTAL_REFRESH = new VcsLogProperty<>(true);
  /**
   * True if ths {@link VcsLogProvider} implementation supports filtering commits by parents count.
   */
  public static final @NotNull VcsLogProperty<Boolean> SUPPORTS_PARENTS_FILTER = new VcsLogProperty<>(true);
}
