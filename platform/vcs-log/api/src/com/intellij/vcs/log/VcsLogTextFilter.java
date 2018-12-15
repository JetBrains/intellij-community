// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

import static com.intellij.vcs.log.VcsLogFilterCollection.TEXT_FILTER;

/**
 * Specifies the log filter by text.
 */
public interface VcsLogTextFilter extends VcsLogDetailsFilter {

  /**
   * Only commits containing the returned text it their commit messages should match the filter.
   */
  @NotNull
  String getText();

  /**
   * Check whether this pattern represents a regular expression.
   *
   * @return true if this pattern represents a regular expression, false otherwise.
   */
  boolean isRegex();

  /**
   * Check whether the filter should be case sensitive.
   *
   * @return true if case sensitive, false otherwise.
   */
  boolean matchesCase();

  /**
   * Checks whether a specified commit message matches this filter.
   *
   * @param message a commit message to check
   * @return true if commit message matches this filter
   */
  boolean matches(@NotNull String message);

  @NotNull
  @Override
  default VcsLogFilterCollection.FilterKey<VcsLogTextFilter> getKey() {
    return TEXT_FILTER;
  }

  @Override
  default boolean matches(@NotNull VcsCommitMetadata details) {
    return matches(details.getFullMessage());
  }

  @NotNull
  @Override
  default String getPresentation() {
    return "'" + getText() + "'";
  }
}
