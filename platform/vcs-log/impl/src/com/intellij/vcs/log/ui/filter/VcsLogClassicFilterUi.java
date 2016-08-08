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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NotNullComputable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SearchTextFieldWithStoredHistory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl;
import com.intellij.vcs.log.impl.VcsLogHashFilterImpl;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 */
public class VcsLogClassicFilterUi implements VcsLogFilterUi {
  private static final String VCS_LOG_TEXT_FILTER_HISTORY = "Vcs.Log.Text.Filter.History";

  private static final String HASH_PATTERN = "[a-fA-F0-9]{7,}";
  private static final Logger LOG = Logger.getInstance(VcsLogClassicFilterUi.class);

  @NotNull private final VcsLogUiImpl myUi;

  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogUiProperties myUiProperties;

  @NotNull private VcsLogDataPack myDataPack;

  @NotNull private final BranchFilterModel myBranchFilterModel;
  @NotNull private final FilterModel<VcsLogUserFilter> myUserFilterModel;
  @NotNull private final FilterModel<VcsLogDateFilter> myDateFilterModel;
  @NotNull private final FilterModel<VcsLogFileFilter> myStructureFilterModel;
  @NotNull private final TextFilterModel myTextFilterModel;

  public VcsLogClassicFilterUi(@NotNull VcsLogUiImpl ui,
                               @NotNull VcsLogData logData,
                               @NotNull VcsLogUiProperties uiProperties,
                               @NotNull VcsLogDataPack initialDataPack) {
    myUi = ui;
    myLogData = logData;
    myUiProperties = uiProperties;
    myDataPack = initialDataPack;

    NotNullComputable<VcsLogDataPack> dataPackGetter = () -> myDataPack;
    myBranchFilterModel = new BranchFilterModel(dataPackGetter);
    myUserFilterModel = new FilterModel<>(dataPackGetter);
    myDateFilterModel = new FilterModel<>(dataPackGetter);
    myStructureFilterModel = new FilterModel<>(dataPackGetter);
    myTextFilterModel = new TextFilterModel(dataPackGetter);

    updateUiOnFilterChange();
  }

  private void updateUiOnFilterChange() {
    FilterModel[] models = {myBranchFilterModel, myUserFilterModel, myDateFilterModel, myStructureFilterModel, myTextFilterModel};
    for (FilterModel<?> model : models) {
      model.addSetFilterListener(() -> {
        myUi.applyFiltersAndUpdateUi();
        myBranchFilterModel
          .onStructureFilterChanged(new HashSet<>(myLogData.getRoots()), myStructureFilterModel.getFilter());
      });
    }
  }

  public void updateDataPack(@NotNull VcsLogDataPack dataPack) {
    myDataPack = dataPack;
  }

  @NotNull
  public SearchTextField createTextFilter() {
    final SearchTextFieldWithStoredHistory textFilter = new SearchTextFieldWithStoredHistory(VCS_LOG_TEXT_FILTER_HISTORY) {
      @Override
      protected void onFieldCleared() {
        myTextFilterModel.setFilter(null);
      }
    };
    textFilter.setText(myTextFilterModel.getText());
    textFilter.getTextEditor().addActionListener(e -> {
      myTextFilterModel.setFilter(new VcsLogTextFilterImpl(textFilter.getText()));
      textFilter.addCurrentTextToHistory();
    });
    textFilter.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        try {
          myTextFilterModel.setUnsavedText(e.getDocument().getText(0, e.getDocument().getLength()));
        }
        catch (BadLocationException ex) {
          LOG.error(ex);
        }
      }
    });
    String shortcutText = KeymapUtil.getFirstKeyboardShortcutText(VcsLogActionPlaces.VCS_LOG_FOCUS_TEXT_FILTER);
    if (!shortcutText.isEmpty()) {
      textFilter.getTextEditor().setToolTipText("Use " + shortcutText + " to switch between text filter and commits list");
    }
    return textFilter;
  }

  /**
   * Returns filter components which will be added to the Log toolbar.
   */
  @NotNull
  public ActionGroup createActionGroup() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new FilterActionComponent(() -> new BranchFilterPopupComponent(myUi, myUiProperties, myBranchFilterModel).initUi()));
    actionGroup.add(new FilterActionComponent(() -> new UserFilterPopupComponent(myUiProperties, myLogData, myUserFilterModel).initUi()));
    actionGroup.add(new FilterActionComponent(() -> new DateFilterPopupComponent(myDateFilterModel).initUi()));
    actionGroup.add(new FilterActionComponent(
      () -> new StructureFilterPopupComponent(myStructureFilterModel, myUi.getColorManager()).initUi()));
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
                                          myStructureFilterModel.getFilter() == null
                                          ? null
                                          : myStructureFilterModel.getFilter().getStructureFilter(),
                                          myStructureFilterModel.getFilter() == null
                                          ? null
                                          : myStructureFilterModel.getFilter().getRootFilter());
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
      JComponent toolbar = myUi.getToolbar();
      toolbar.revalidate();
      toolbar.repaint();
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

  public static class BranchFilterModel extends FilterModel<VcsLogBranchFilter> {
    @Nullable
    private Collection<VirtualFile> myVisibleRoots;

    BranchFilterModel(@NotNull Computable<VcsLogDataPack> provider) {
      super(provider);
    }

    public void onStructureFilterChanged(@NotNull Set<VirtualFile> roots, @Nullable VcsLogFileFilter filter) {
      if (filter == null) {
        myVisibleRoots = null;
      }
      else {
        myVisibleRoots = VcsLogUtil.getAllVisibleRoots(roots, filter.getRootFilter(), filter.getStructureFilter());
      }
    }

    @Nullable
    public Collection<VirtualFile> getVisibleRoots() {
      return myVisibleRoots;
    }
  }

  private static class TextFilterModel extends FilterModel<VcsLogTextFilter> {
    @Nullable private String myText;

    public TextFilterModel(NotNullComputable<VcsLogDataPack> dataPackProvider) {
      super(dataPackProvider);
    }

    @NotNull
    String getText() {
      if (myText != null) {
        return myText;
      }
      else if (getFilter() != null) {
        return getFilter().getText();
      }
      else {
        return "";
      }
    }

    void setUnsavedText(@NotNull String text) {
      myText = text;
    }

    @Override
    void setFilter(@Nullable VcsLogTextFilter filter) {
      super.setFilter(filter);
      myText = null;
    }
  }
}
