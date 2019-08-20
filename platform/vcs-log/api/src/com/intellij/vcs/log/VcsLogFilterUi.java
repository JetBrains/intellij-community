package com.intellij.vcs.log;

import com.intellij.ui.SearchTextField;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Graphical UI for filtering commits in the log.
 */
@ApiStatus.Experimental
public interface VcsLogFilterUi {

  /**
   * Returns the filters currently active, i.e. switched on by user.
   */
  @NotNull
  VcsLogFilterCollection getFilters();

  /**
   * Sets the given filter to the given value and updates the log view. <br/>
   */
  void setFilter(@Nullable VcsLogFilter filter);

  @NotNull
  SearchTextField getTextFilterComponent();
}
