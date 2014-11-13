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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NotNullComputable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SearchTextFieldWithStoredHistory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl;
import com.intellij.vcs.log.impl.VcsLogHashFilterImpl;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 */
public class VcsLogClassicFilterUi implements VcsLogFilterUi {

  private static final String HASH_PATTERN = "[a-fA-F0-9]{7,}";

  @NotNull private final VcsLogUiImpl myUi;

  @NotNull private final VcsLogDataHolder myLogDataHolder;
  @NotNull private final VcsLogUiProperties myUiProperties;

  @NotNull private VcsLogDataPack myDataPack;

  @NotNull private final FilterModel<VcsLogBranchFilter> myBranchFilterModel;
  @NotNull private final FilterModel<VcsLogUserFilter> myUserFilterModel;
  @NotNull private final FilterModel<VcsLogDateFilter> myDateFilterModel;
  @NotNull private final FilterModel<VcsLogStructureFilter> myStructureFilterModel;
  @NotNull private final FilterModel<VcsLogTextFilter> myTextFilterModel;

  public VcsLogClassicFilterUi(@NotNull VcsLogUiImpl ui,
                               @NotNull VcsLogDataHolder logDataHolder,
                               @NotNull VcsLogUiProperties uiProperties,
                               @NotNull VcsLogDataPack initialDataPack) {
    myUi = ui;
    myLogDataHolder = logDataHolder;
    myUiProperties = uiProperties;
    myDataPack = initialDataPack;

    NotNullComputable<VcsLogDataPack> dataPackGetter = new NotNullComputable<VcsLogDataPack>() {
      @NotNull
      @Override
      public VcsLogDataPack compute() {
        return myDataPack;
      }
    };
    myBranchFilterModel = new FilterModel<VcsLogBranchFilter>(dataPackGetter);
    myUserFilterModel = new FilterModel<VcsLogUserFilter>(dataPackGetter);
    myDateFilterModel = new FilterModel<VcsLogDateFilter>(dataPackGetter);
    myStructureFilterModel = new FilterModel<VcsLogStructureFilter>(dataPackGetter);
    myTextFilterModel = new FilterModel<VcsLogTextFilter>(dataPackGetter);

    updateUiOnFilterChange();
  }

  private void updateUiOnFilterChange() {
    FilterModel[] models = {myBranchFilterModel, myUserFilterModel, myDateFilterModel, myStructureFilterModel, myTextFilterModel};
    for (FilterModel<?> model : models) {
      model.addSetFilterListener(new Runnable() {
        @Override
        public void run() {
          myUi.applyFiltersAndUpdateUi();
        }
      });
    }
  }

  public void updateDataPack(@NotNull VcsLogDataPack dataPack) {
    myDataPack = dataPack;
  }

  /**
   * Returns filter components which will be added to the Log toolbar.
   */
  @NotNull
  public ActionGroup createActionGroup() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new TextFilterComponent(myTextFilterModel));
    actionGroup.add(new FilterActionComponent(new Computable<JComponent>() {
      @Override
      public JComponent compute() {
        return new BranchFilterPopupComponent(myUiProperties, myBranchFilterModel);
      }
    }));
    actionGroup.add(new FilterActionComponent(new Computable<JComponent>() {
      @Override
      public JComponent compute() {
        return new UserFilterPopupComponent(myUiProperties, myLogDataHolder, myUserFilterModel);
      }
    }));
    actionGroup.add(new FilterActionComponent(new Computable<JComponent>() {
      @Override
      public JComponent compute() {
        return new DateFilterPopupComponent(myDateFilterModel);
      }
    }));
    actionGroup.add(new FilterActionComponent(new Computable<JComponent>() {
      @Override
      public JComponent compute() {
        return new StructureFilterPopupComponent(myStructureFilterModel);
      }
    }));
    return actionGroup;
  }

  @NotNull
  @Override
  public VcsLogFilterCollection getFilters() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Pair<VcsLogTextFilter, VcsLogHashFilter> filtersFromText = getFiltersFromTextArea(myTextFilterModel.getFilter());
    return new VcsLogFilterCollectionImpl(myBranchFilterModel.getFilter(),
                                          myUserFilterModel.getFilter(),
                                          filtersFromText.second,
                                          myDateFilterModel.getFilter(),
                                          filtersFromText.first,
                                          myStructureFilterModel.getFilter());
  }

  @NotNull
  private static Pair<VcsLogTextFilter, VcsLogHashFilter> getFiltersFromTextArea(@Nullable VcsLogTextFilter filter) {
    if (filter == null) {
      return Pair.empty();
    }
    String text = filter.getText().trim();
    if (StringUtil.isEmptyOrSpaces(text)) {
      return Pair.empty();
    }
    List<String> hashes = ContainerUtil.newArrayList();
    for (String word : StringUtil.split(text, " ")) {
      if (!StringUtil.isEmptyOrSpaces(word) && word.matches(HASH_PATTERN)) {
        hashes.add(word);
      }
      else {
        break;
      }
    }

    VcsLogTextFilter textFilter;
    VcsLogHashFilterImpl hashFilter;
    if (!hashes.isEmpty()) { // text is ignored if there are hashes in the text
      textFilter = null;
      hashFilter = new VcsLogHashFilterImpl(hashes);
    }
    else {
      textFilter = new VcsLogTextFilterImpl(text);
      hashFilter = null;
    }
    return Pair.<VcsLogTextFilter, VcsLogHashFilter>create(textFilter, hashFilter);
  }

  @Override
  public void setFilter(@NotNull VcsLogFilter filter) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (filter instanceof VcsLogBranchFilter) {
      myBranchFilterModel.setFilter((VcsLogBranchFilter)filter);
      JComponent toolbar = myUi.getMainFrame().getToolbar();
      toolbar.revalidate();
      toolbar.repaint();
    }
  }

  private static class TextFilterComponent extends DumbAwareAction implements CustomComponentAction {

    private final FilterModel<VcsLogTextFilter> myFilterModel;

    public TextFilterComponent(FilterModel<VcsLogTextFilter> filterModel) {
      myFilterModel = filterModel;
    }

    @Override
    public JComponent createCustomComponent(Presentation presentation) {
      JPanel panel = new JPanel();
      JLabel filterCaption = new JLabel("Filter:");
      filterCaption.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getInactiveTextColor());
      panel.add(filterCaption);
      panel.add(createSearchField());
      return panel;
    }

    private Component createSearchField() {
      final SearchTextFieldWithStoredHistory textFilter = new SearchTextFieldWithStoredHistory("Vcs.Log.Text.Filter.History") {
        @Override
        protected void onFieldCleared() {
          myFilterModel.setFilter(null);
        }
      };
      textFilter.getTextEditor().addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(@NotNull ActionEvent e) {
          myFilterModel.setFilter(new VcsLogTextFilterImpl(textFilter.getText()));
          textFilter.addCurrentTextToHistory();
        }
      });
      return textFilter;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
    }
  }

  private static class FilterActionComponent extends DumbAwareAction implements CustomComponentAction {

    @NotNull private final Computable<JComponent> myComponentCreator;

    public FilterActionComponent(@NotNull Computable<JComponent> componentCreator) {
      myComponentCreator = componentCreator;
    }

    @Override
    public JComponent createCustomComponent(Presentation presentation) {
      return myComponentCreator.compute();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
    }
  }
}
