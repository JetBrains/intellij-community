// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.vcs.log.VcsLogFilterCollection.ROOT_FILTER;

/**
 * Tells the log to filter by vcs roots.
 */
public interface VcsLogRootFilter extends VcsLogFilter {

  /**
   * Returns vcs roots that are visible.
   */
  @NotNull
  Collection<VirtualFile> getRoots();

  @Override
  default @NotNull VcsLogFilterCollection.FilterKey<VcsLogRootFilter> getKey() {
    return ROOT_FILTER;
  }

  @Override
  default @NotNull String getDisplayText() {
    return StringUtil.join(getRoots(), VirtualFile::getName, ", ");
  }
}
