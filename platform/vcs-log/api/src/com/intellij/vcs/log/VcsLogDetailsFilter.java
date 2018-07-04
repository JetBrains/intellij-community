package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

/**
 * Filter which needs {@link VcsCommitMetadata} to work.
 */
public interface VcsLogDetailsFilter extends VcsLogFilter {

  boolean matches(@NotNull VcsCommitMetadata details);
}
