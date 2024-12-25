// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.vcs.log.VcsLogFilterCollection.REVISION_FILTER;

/**
 * Tells to filter by head commits.
 */
public interface VcsLogRevisionFilter extends VcsLogBranchLikeFilter {

  @NotNull
  Collection<CommitId> getHeads();

  @Override
  default @NotNull VcsLogFilterCollection.FilterKey<VcsLogRevisionFilter> getKey() {
    return REVISION_FILTER;
  }

  @Override
  default @NotNull String getDisplayText() {
    return StringUtil.join(getHeads(), commit -> commit.getHash().toShortString(), ", ");
  }
}
