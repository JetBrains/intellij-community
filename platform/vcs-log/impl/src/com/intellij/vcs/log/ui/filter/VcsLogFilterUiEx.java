// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.ui.SearchTextField;
import com.intellij.util.ui.StatusText;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogFilterUi;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface VcsLogFilterUiEx extends VcsLogFilterUi {

  /**
   * Sets filters to the given value and updates the log view.
   */
  void setFilters(@NotNull VcsLogFilterCollection collection);

  /**
   * Clears log filters.
   */
  default void clearFilters() {
    setFilters(VcsLogFilterObject.EMPTY_COLLECTION);
  }

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

  /**
   * Customizes the empty text which is shown in the middle of the table, if there are no commits to display.
   * <p/>
   * Returns true if the custom empty text has been set by this filter UI, returns false if general rules should be applied.
   * <p/>
   * NB: In the case of error this method is not called, and the general logic is used to show the error in the empty space.
   */
  default void setCustomEmptyText(@NotNull StatusText text) {
    text.setText(VcsLogBundle.message("vcs.log.no.commits.matching.status"));
    VcsLogUiUtil.appendResetFiltersActionToEmptyText(this, text);
  }
}