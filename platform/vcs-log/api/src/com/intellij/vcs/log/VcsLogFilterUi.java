package com.intellij.vcs.log;

import org.jetbrains.annotations.NotNull;

/**
 * Graphical UI for filtering commits in the log.
 */
public interface VcsLogFilterUi {

  /**
   * Returns the filters currently active, i.e. switched on by user.
   */
  @NotNull
  VcsLogFilterCollection getFilters();

  /**
   * Sets the given filter to the given value and updates the log view. <br/>
   * <b>Note:</b> only VcsLogBranchFilter is currently supported.
   */
  void setFilter(@NotNull VcsLogFilter filter);

}
