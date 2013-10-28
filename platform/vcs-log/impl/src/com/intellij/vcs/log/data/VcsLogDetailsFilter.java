package com.intellij.vcs.log.data;

import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogFilter;
import org.jetbrains.annotations.NotNull;

/**
 * Filter which needs {@link VcsFullCommitDetails} to work.
 *
 * @see VcsLogGraphFilter
 */
public interface VcsLogDetailsFilter extends VcsLogFilter {

  boolean matches(@NotNull VcsFullCommitDetails details);

}
