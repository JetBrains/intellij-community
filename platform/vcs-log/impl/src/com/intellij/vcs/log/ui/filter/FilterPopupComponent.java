// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for components which allow to set up filter for the VCS Log, by displaying a popup with available choices.
 */
abstract class FilterPopupComponent<Filter, Model extends FilterModel<Filter>> extends VcsLogPopupComponent {

  /**
   * Special value that indicates that no filtering is on.
   */
  protected static final String ALL = "All";
  @NotNull protected final Model myFilterModel;

  FilterPopupComponent(@NotNull String filterName, @NotNull Model filterModel) {
    super(filterName);
    myFilterModel = filterModel;
  }

  @Override
  public String getCurrentText() {
    Filter filter = myFilterModel.getFilter();
    return filter == null ? ALL : getText(filter);
  }

  @Override
  public void installChangeListener(@NotNull Runnable onChange) {
    myFilterModel.addSetFilterListener(onChange);
  }

  @NotNull
  protected abstract String getText(@NotNull Filter filter);

  @Nullable
  protected abstract String getToolTip(@NotNull Filter filter);

  @Override
  public String getToolTipText() {
    Filter filter = myFilterModel.getFilter();
    return filter == null ? null : getToolTip(filter);
  }

  /**
   * Returns the special action that indicates that no filtering is selected in this component.
   */
  @NotNull
  protected AnAction createAllAction() {
    return new AllAction();
  }

  private class AllAction extends DumbAwareAction {

    AllAction() {
      super(ALL);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myFilterModel.setFilter(null);
    }
  }
}
