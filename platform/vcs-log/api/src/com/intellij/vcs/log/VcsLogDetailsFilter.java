package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

/**
 * Filter which needs {@link VcsFullCommitDetails} to work.
 *
 * @see VcsLogGraphFilter
 */
public interface VcsLogDetailsFilter extends VcsLogFilter {

  boolean matches(@NotNull VcsFullCommitDetails details);

}
