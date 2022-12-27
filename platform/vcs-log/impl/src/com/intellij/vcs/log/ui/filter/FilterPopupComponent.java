// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Base class for components which allow to set up filter for the VCS Log, by displaying a popup with available choices.
 */
abstract class FilterPopupComponent<Filter, Model extends FilterModel<Filter>> extends VcsLogPopupComponent {

  /**
   * Special value that indicates that no filtering is on.
   */
  protected static final Supplier<@Nls String> EMPTY_FILTER_TEXT = () -> "";

  protected static final Supplier<@Nls String> ALL_ACTION_TEXT = VcsLogBundle.messagePointer("vcs.log.filter.all");

  @NotNull protected final Model myFilterModel;

  FilterPopupComponent(@NotNull Supplier<@NlsContexts.Label @NotNull String> displayName, @NotNull Model filterModel) {
    super(displayName);
    myFilterModel = filterModel;
  }

  @Override
  public @NotNull String getCurrentText() {
    Filter filter = myFilterModel.getFilter();
    return filter == null ? getEmptyFilterValue() : getText(filter);
  }

  @Nls
  @Override
  public @NotNull String getEmptyFilterValue() {
    return EMPTY_FILTER_TEXT.get();
  }

  @Override
  protected boolean isValueSelected() {
    return myFilterModel.getFilter() != null;
  }

  @Override
  public void installChangeListener(@NotNull Runnable onChange) {
    myFilterModel.addSetFilterListener(onChange);
  }

  @NotNull
  @Nls
  protected abstract String getText(@NotNull Filter filter);

  @Nullable
  @NlsContexts.Tooltip
  protected abstract String getToolTip(@NotNull Filter filter);

  @Override
  @NlsContexts.Tooltip
  public String getToolTipText() {
    Filter filter = myFilterModel.getFilter();
    return filter == null ? null : getToolTip(filter);
  }

  @Override
  protected Runnable createResetAction() {
    return () -> {
      myFilterModel.setFilter(null);
      VcsLogUsageTriggerCollector.triggerFilterReset(VcsLogUsageTriggerCollector.FilterResetType.CLOSE_BUTTON);
    };
  }
}
