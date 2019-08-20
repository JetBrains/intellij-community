package com.intellij.vcs.log;

import com.intellij.openapi.actionSystem.ActionGroup;
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

  /**
   * Returns filter components which will be added to the Log toolbar.
   */
  @NotNull
  ActionGroup createActionGroup();

  @NotNull
  SearchTextField getTextFilterComponent();

  /**
   * Informs the filter UI components that the actual VcsLogDataPack has been updated (e.g. due to a log refresh).
   * Components may want to update their fields and/or rebuild.
   */
  void updateDataPack(@NotNull VcsLogDataPack newDataPack);

}
