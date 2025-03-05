// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.vcs.log.VcsLogFilterCollection.BRANCH_FILTER;

/**
 * Tells to filter by branches with given names.
 */
public interface VcsLogBranchFilter extends VcsLogBranchLikeFilter {
  /**
   * Tells if a branch matches the filter.
   *
   * @param name branch name.
   * @return true if a branch matches the filter, false otherwise.
   */
  boolean matches(@NotNull String name);

  /**
   * Text presentation for the filter (to display in filter popup).
   *
   * @return text presentation for the filter.
   */
  @NotNull
  Collection<@NlsSafe String> getTextPresentation();

  /**
   * @return true if filter has no patterns
   */
  boolean isEmpty();

  @Override
  default @NotNull VcsLogFilterCollection.FilterKey<VcsLogBranchFilter> getKey() {
    return BRANCH_FILTER;
  }

  @Override
  default @NotNull String getDisplayText() {
    return StringUtil.join(getTextPresentation(), ", ");
  }
}
