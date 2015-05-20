/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.ui.VcsLogPopupComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for components which allow to set up filter for the VCS Log, by displaying a popup with available choices.
 */
abstract class FilterPopupComponent<Filter extends VcsLogFilter> extends VcsLogPopupComponent {

  /**
   * Special value that indicates that no filtering is on.
   */
  protected static final String ALL = "All";
  @NotNull protected final FilterModel<Filter> myFilterModel;

  FilterPopupComponent(@NotNull String filterName, @NotNull FilterModel<Filter> filterModel) {
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
