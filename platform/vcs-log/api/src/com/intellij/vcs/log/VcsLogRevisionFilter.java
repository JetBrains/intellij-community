// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.vcs.log.VcsLogFilterCollection.REVISION_FILTER;

/**
 * Tells to filter by head commits.
 */
public interface VcsLogRevisionFilter extends VcsLogFilter {

  @NotNull
  Collection<CommitId> getHeads();

  @NotNull
  @Override
  default VcsLogFilterCollection.FilterKey<VcsLogRevisionFilter> getKey() {
    return REVISION_FILTER;
  }

  @NotNull
  @Override
  default String getPresentation() {
    return StringUtil.join(getHeads(), commit -> commit.getHash().toShortString(), ", ");
  }
}
