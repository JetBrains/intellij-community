// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NamedRunnable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.ui.navigation.History;
import com.intellij.util.PairFunction;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogHighlighter;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.actions.ActionController;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties.VcsLogHighlighterProperty;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.filter.VcsLogClassicFilterUi;
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx;
import com.intellij.vcs.log.ui.frame.MainFrame;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.ui.table.column.TableColumnWidthProperty;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

public class VcsLogUiImpl extends AbstractVcsLogUi implements MainVcsLogUi {
  private static final @NonNls String HELP_ID = "reference.changesToolWindow.log";

  private final @NotNull MainVcsLogUiProperties myUiProperties;
  private final @NotNull MainFrame myMainFrame;
  private final @NotNull MyVcsLogUiPropertiesListener myPropertiesListener;
  private final @NotNull History myHistory;
  private final @NotNull LinkedHashMap<String, VcsLogHighlighter> myHighlighters = new LinkedHashMap<>();

  public VcsLogUiImpl(@NotNull String id,
                      @NotNull VcsLogData logData,
                      @NotNull VcsLogColorManager manager,
                      @NotNull MainVcsLogUiProperties uiProperties,
                      @NotNull VisiblePackRefresher refresher,
                      @Nullable VcsLogFilterCollection initialFilters) {
    this(id, logData, manager, uiProperties, refresher, initialFilters, true);
  }

  public VcsLogUiImpl(@NotNull String id,
                      @NotNull VcsLogData logData,
                      @NotNull VcsLogColorManager manager,
                      @NotNull MainVcsLogUiProperties uiProperties,
                      @NotNull VisiblePackRefresher refresher,
                      @Nullable VcsLogFilterCollection initialFilters,
                      boolean isEditorDiffPreview) {
    super(id, logData, manager, refresher);
    myUiProperties = uiProperties;

    VcsLogFilterUiEx filterUi = createFilterUi(filters -> applyFiltersAndUpdateUi(filters), initialFilters, this);
    myMainFrame = createMainFrame(logData, uiProperties, filterUi, isEditorDiffPreview);

    LOG_HIGHLIGHTER_FACTORY_EP.addChangeListener(this::updateHighlighters, this);
    ApplicationManager.getApplication().invokeLater(this::updateHighlighters, o -> myDisposableFlag.isDisposed());

    myPropertiesListener = new MyVcsLogUiPropertiesListener();
    myUiProperties.addChangeListener(myPropertiesListener, this);

    myHistory = VcsLogUiUtil.installNavigationHistory(this);

    applyFiltersAndUpdateUi(myMainFrame.getFilterUi().getFilters());
  }

  protected @NotNull MainFrame createMainFrame(@NotNull VcsLogData logData,
                                               @NotNull MainVcsLogUiProperties uiProperties,
                                               @NotNull VcsLogFilterUiEx filterUi,
                                               boolean isEditorDiffPreview) {
    return new MainFrame(logData, this, uiProperties, filterUi, isEditorDiffPreview, this);
  }

  protected @NotNull VcsLogFilterUiEx createFilterUi(@NotNull Consumer<VcsLogFilterCollection> filterConsumer,
                                                     @Nullable VcsLogFilterCollection filters,
                                                     @NotNull Disposable parentDisposable) {
    return new VcsLogClassicFilterUi(myLogData, filterConsumer, myUiProperties, myColorManager, filters, parentDisposable);
  }

  @Override
  protected void onVisiblePackUpdated(boolean permGraphChanged) {
    myMainFrame.updateDataPack(myVisiblePack, permGraphChanged);
    myPropertiesListener.onShowLongEdgesChanged();
  }

  protected @NotNull MainFrame getMainFrame() {
    return myMainFrame;
  }

  @Override
  protected <T> void handleCommitNotFound(@NotNull T commitId,
                                          boolean commitExists,
                                          @NotNull PairFunction<? super VisiblePack, ? super T, Integer> rowGetter) {
    if (getFilterUi().getFilters().isEmpty() || !commitExists) {
      super.handleCommitNotFound(commitId, commitExists, rowGetter);
      return;
    }

    List<NamedRunnable> runnables = new ArrayList<>();
    runnables.add(new NamedRunnable(VcsLogBundle.message("vcs.log.commit.does.not.match.view.and.reset.link")) {
      @Override
      public void run() {
        getFilterUi().clearFilters();
        invokeOnChange(() -> jumpTo(commitId, rowGetter, SettableFuture.create(), false, true),
                       pack -> pack.getFilters().isEmpty());
      }
    });
    VcsProjectLog projectLog = VcsProjectLog.getInstance(myProject);
    if (projectLog.getDataManager() == myLogData) {
      runnables.add(new NamedRunnable(VcsLogBundle.message("vcs.log.commit.does.not.match.view.in.tab.link")) {
        @Override
        public void run() {
          MainVcsLogUi ui = projectLog.openLogTab(VcsLogFilterObject.collection());
          if (ui != null) {
            VcsLogUtil.invokeOnChange(ui, () -> ui.jumpTo(commitId, rowGetter, SettableFuture.create(), false, true),
                                      pack -> pack.getFilters().isEmpty());
          }
        }
      });
    }
    VcsBalloonProblemNotifier.showOverChangesView(myProject, getCommitNotFoundMessage(commitId, true), MessageType.WARNING,
                                                  runnables.toArray(new NamedRunnable[0]));
  }

  public boolean isHighlighterEnabled(@NotNull String id) {
    VcsLogHighlighterProperty property = VcsLogHighlighterProperty.get(id);
    return myUiProperties.exists(property) && myUiProperties.get(property);
  }

  protected void applyFiltersAndUpdateUi(@NotNull VcsLogFilterCollection filters) {
    myRefresher.onFiltersChange(filters);

    JComponent toolbar = myMainFrame.getToolbar();
    toolbar.revalidate();
    toolbar.repaint();
  }

  @Override
  public @NotNull VcsLogGraphTable getTable() {
    return myMainFrame.getGraphTable();
  }

  @Override
  public @NotNull JComponent getMainComponent() {
    return myMainFrame;
  }

  @Override
  public @NotNull VcsLogFilterUiEx getFilterUi() {
    return myMainFrame.getFilterUi();
  }

  @Override
  public @NotNull ChangesBrowserBase getChangesBrowser() {
    return myMainFrame.getChangesBrowser();
  }

  @Override
  public @NotNull JComponent getToolbar() {
    return myMainFrame.getToolbar();
  }

  @Override
  public @NotNull MainVcsLogUiProperties getProperties() {
    return myUiProperties;
  }

  @Override
  public @Nullable String getHelpId() {
    return HELP_ID;
  }

  @Override
  public @Nullable History getNavigationHistory() {
    return myHistory;
  }

  @Override
  public void selectFilePath(@NotNull FilePath filePath, boolean requestFocus) {
    getMainFrame().selectFilePath(filePath, requestFocus);
  }

  private void updateHighlighters() {
    myHighlighters.forEach((s, highlighter) -> getTable().removeHighlighter(highlighter));
    myHighlighters.clear();

    for (VcsLogHighlighterFactory factory : LOG_HIGHLIGHTER_FACTORY_EP.getExtensionList()) {
      VcsLogHighlighter highlighter = factory.createHighlighter(myLogData, this);
      myHighlighters.put(factory.getId(), highlighter);
      if (isHighlighterEnabled(factory.getId())) {
        getTable().addHighlighter(highlighter);
      }
    }

    getTable().repaint();
  }

  private class MyVcsLogUiPropertiesListener implements VcsLogUiProperties.PropertiesChangeListener {

    @Override
    public <T> void onPropertyChanged(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
      if (CommonUiProperties.SHOW_DETAILS.equals(property)) {
        myMainFrame.showDetails(myUiProperties.get(CommonUiProperties.SHOW_DETAILS));
      }
      else if (MainVcsLogUiProperties.SHOW_LONG_EDGES.equals(property)) {
        onShowLongEdgesChanged();
      }
      else if (MainVcsLogUiProperties.BEK_SORT_TYPE.equals(property)) {
        myRefresher.onSortTypeChange(myUiProperties.get(MainVcsLogUiProperties.BEK_SORT_TYPE));
      }
      else if (CommonUiProperties.COLUMN_ID_ORDER.equals(property)) {
        getTable().onColumnOrderSettingChanged();
      }
      else if (property instanceof VcsLogHighlighterProperty) {
        VcsLogHighlighter highlighter = myHighlighters.get(((VcsLogHighlighterProperty)property).getId());
        if ((boolean)myUiProperties.get(property)) {
          getTable().addHighlighter(highlighter);
        }
        else {
          getTable().removeHighlighter(highlighter);
        }
        getTable().repaint();
      }
      else if (property instanceof TableColumnWidthProperty) {
        getTable().forceReLayout(((TableColumnWidthProperty)property).getColumn());
      }
    }

    private void onShowLongEdgesChanged() {
      ActionController<Integer> actionController = myVisiblePack.getVisibleGraph().getActionController();
      boolean oldLongEdgesHiddenValue = actionController.areLongEdgesHidden();
      boolean newLongEdgesHiddenValue = !myUiProperties.get(MainVcsLogUiProperties.SHOW_LONG_EDGES);
      if (newLongEdgesHiddenValue != oldLongEdgesHiddenValue) {
        actionController.setLongEdgesHidden(newLongEdgesHiddenValue);
        getTable().repaint();
      }
    }
  }
}
