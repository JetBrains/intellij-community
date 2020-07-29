// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

public final class VcsLogProperties {
  public static class VcsLogProperty<T> {
    private final @NotNull T defaultValue;

    private VcsLogProperty(@NotNull T defaultValue) {
      this.defaultValue = defaultValue;
    }

    @NotNull
    public T getOrDefault(VcsLogProvider provider) {
      T value = provider.getPropertyValue(this);
      return value == null ? defaultValue : value;
    }
  }

  @NotNull public static final VcsLogProperty<Boolean> LIGHTWEIGHT_BRANCHES = new VcsLogProperty<>(false);
  @NotNull public static final VcsLogProperty<Boolean> SUPPORTS_INDEXING = new VcsLogProperty<>(false);
  @NotNull public static final VcsLogProperty<Boolean> SUPPORTS_LOG_DIRECTORY_HISTORY = new VcsLogProperty<>(false);
  @NotNull public static final VcsLogProperty<Boolean> CASE_INSENSITIVE_REGEX = new VcsLogProperty<>(true);
  /**
   * True if VCS has separate committer and committed date information which may differ from author and author date
   */
  @NotNull public static final VcsLogProperty<Boolean> HAS_COMMITTER = new VcsLogProperty<>(false);

  /**
   * @deprecated use {@link VcsLogProperty#getOrDefault(VcsLogProvider)}
   */
  @Deprecated
  @NotNull
  public static <T> T get(@NotNull VcsLogProvider provider, VcsLogProperty<T> property) {
    return property.getOrDefault(provider);
  }
}
