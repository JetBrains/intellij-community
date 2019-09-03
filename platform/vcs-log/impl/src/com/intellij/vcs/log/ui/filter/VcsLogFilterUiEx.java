// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.ui.SearchTextField;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.VcsLogFilterUi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface VcsLogFilterUiEx extends VcsLogFilterUi {

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
