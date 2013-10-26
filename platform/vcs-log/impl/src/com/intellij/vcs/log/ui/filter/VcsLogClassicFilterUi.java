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

import com.intellij.ui.SearchTextField;
import com.intellij.ui.SearchTextFieldWithStoredHistory;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.data.VcsLogFilterer;
import com.intellij.vcs.log.ui.VcsLogUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 */
public class VcsLogClassicFilterUi implements VcsLogFilterUi {

  @NotNull private final VcsLogFilterer myFilterer;
  @NotNull private final JComponent myRootPanel;
  @NotNull private final List<FilterPopupComponent> myFilterPopupComponents;
  @NotNull private final SearchTextField myTextFilter;

  public VcsLogClassicFilterUi(@NotNull VcsLogUI ui) {
    myFilterer = ui.getFilterer();

    JLabel filterCaption = new JLabel("Filter:");
    filterCaption.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getInactiveTextColor());

    myTextFilter = new SearchTextFieldWithStoredHistory("Vcs.Log.Text.Filter.History");
    myTextFilter.getTextEditor().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        applyFilters();
        myTextFilter.addCurrentTextToHistory();
      }
    });
    FilterPopupComponent branchFilter = new BranchFilterPopupComponent(this, ui);
    FilterPopupComponent userFilter = new UserFilterPopupComponent(this, ui.getLogDataHolder(), ui.getUiProperties());

    myFilterPopupComponents = ContainerUtil.newArrayList();
    myFilterPopupComponents.add(branchFilter);
    myFilterPopupComponents.add(userFilter);

    myRootPanel = new JPanel();
    myRootPanel.add(filterCaption);
    myRootPanel.add(myTextFilter);
    myRootPanel.add(branchFilter);
    myRootPanel.add(userFilter);
  }

  @NotNull
  @Override
  public JComponent getRootComponent() {
    return myRootPanel;
  }

  @NotNull
  @Override
  public Collection<VcsLogFilter> getFilters() {
    List<VcsLogFilter> filters = getPopupFilters();
    if (!myTextFilter.getText().isEmpty()) {
      filters.add(new VcsLogTextFilter(myTextFilter.getText()));
    }
    return filters;
  }

  @NotNull
  private List<VcsLogFilter> getPopupFilters() {
    return new ArrayList<VcsLogFilter>(ContainerUtil.mapNotNull(myFilterPopupComponents,
                                                                new Function<FilterPopupComponent, VcsLogFilter>() {
                                                                  @Override
                                                                  public VcsLogFilter fun(FilterPopupComponent filterComponent) {
                                                                    return filterComponent.getFilter();
                                                                  }
                                                                }));
  }

  void applyFilters() {
    myFilterer.applyFiltersAndUpdateUi(getFilters());
  }

}
