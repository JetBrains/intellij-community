// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.vcs.log.VcsLogFilterCollection.BRANCH_FILTER;

/**
 * Tells to filter by branches with given names.
 */
public interface VcsLogBranchFilter extends VcsLogFilter {
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
  Collection<String> getTextPresentation();

  @NotNull
  @Override
  default VcsLogFilterCollection.FilterKey<VcsLogBranchFilter> getKey() {
    return BRANCH_FILTER;
  }

  @NotNull
  @Override
  default String getPresentation() {
    return StringUtil.join(getTextPresentation(), ", ");
  }
}
