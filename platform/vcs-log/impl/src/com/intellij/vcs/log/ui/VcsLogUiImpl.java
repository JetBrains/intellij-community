// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NamedRunnable;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.ui.navigation.History;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.PairFunction;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties.VcsLogHighlighterProperty;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.filter.VcsLogClassicFilterUi;
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx;
import com.intellij.vcs.log.ui.frame.MainFrame;
import com.intellij.vcs.log.ui.frame.VcsLogEditorDiffPreview;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import com.intellij.vcs.log.ui.table.VcsLogColumn;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class VcsLogUiImpl extends AbstractVcsLogUi implements MainVcsLogUi {
  @NonNls private static final String HELP_ID = "reference.changesToolWindow.log";

  @NotNull private final MainVcsLogUiProperties myUiProperties;
  @NotNull private final MainFrame myMainFrame;
  @NotNull private final MyVcsLogUiPropertiesListener myPropertiesListener;
  @NotNull private final History myHistory;
  @NotNull private final EventDispatcher<VcsLogFilterListener> myFilterListenerDispatcher =
    EventDispatcher.create(VcsLogFilterListener.class);

  public VcsLogUiImpl(@NotNull String id,
                      @NotNull VcsLogData logData,
                      @NotNull VcsLogColorManager manager,
                      @NotNull MainVcsLogUiProperties uiProperties,
                      @NotNull VisiblePackRefresher refresher,
                      @Nullable VcsLogFilterCollection initialFilters) {
    super(id, logData, manager, refresher);
    myUiProperties = uiProperties;

    VcsLogFilterUiEx filterUi = createFilterUi(filters -> applyFiltersAndUpdateUi(filters), initialFilters, this);
    myMainFrame = createMainFrame(logData, uiProperties, filterUi);

    VcsLogUiUtil.installHighlighters(this, f -> true);

    myPropertiesListener = new MyVcsLogUiPropertiesListener();
    myUiProperties.addChangeListener(myPropertiesListener);

    myHistory = VcsLogUiUtil.installNavigationHistory(this);

    applyFiltersAndUpdateUi(myMainFrame.getFilterUi().getFilters());
  }

  @NotNull
  protected MainFrame createMainFrame(@NotNull VcsLogData logData,
                                      @NotNull MainVcsLogUiProperties uiProperties, @NotNull VcsLogFilterUiEx filterUi) {
    boolean isDiffPreviewAsEditor = VcsLogUiUtil.isDiffPreviewInEditor();
    MainFrame mainFrame = new MainFrame(logData, this, uiProperties, filterUi, !isDiffPreviewAsEditor, this);
    if (isDiffPreviewAsEditor) {
      new VcsLogEditorDiffPreview(myProject, myUiProperties, mainFrame);
    }
    return mainFrame;
  }

  @NotNull
  protected VcsLogFilterUiEx createFilterUi(@NotNull Consumer<VcsLogFilterCollection> filterConsumer,
                                            @Nullable VcsLogFilterCollection filters,
                                            @NotNull Disposable parentDisposable) {
    return new VcsLogClassicFilterUi(myLogData, filterConsumer, myUiProperties, myColorManager, filters, parentDisposable);
  }

  @Override
  protected void onVisiblePackUpdated(boolean permGraphChanged) {
    myMainFrame.updateDataPack(myVisiblePack, permGraphChanged);
    myPropertiesListener.onShowLongEdgesChanged();
  }

  @NotNull
  protected MainFrame getMainFrame() {
    return myMainFrame;
  }

  @Override
  protected <T> void handleCommitNotFound(@NotNull T commitId,
                                          boolean commitExists,
                                          @NotNull PairFunction<GraphTableModel, T, Integer> rowGetter) {
    if (getFilterUi().getFilters().isEmpty() || !commitExists) {
      super.handleCommitNotFound(commitId, commitExists, rowGetter);
      return;
    }

    List<NamedRunnable> runnables = new ArrayList<>();
    runnables.add(new NamedRunnable(VcsLogBundle.message("vcs.log.commit.does.not.match.view.and.reset.link")) {
      @Override
      public void run() {
        getFilterUi().clearFilters();
        invokeOnChange(() -> jumpTo(commitId, rowGetter, SettableFuture.create(), false),
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
            ui.invokeOnChange(() -> ui.jumpTo(commitId, rowGetter, SettableFuture.create(), false),
                              pack -> pack.getFilters().isEmpty());
          }
        }
      });
    }
    VcsBalloonProblemNotifier.showOverChangesView(myProject, getCommitNotFoundMessage(commitId, true), MessageType.WARNING,
                                                  runnables.toArray(new NamedRunnable[0]));
  }

  @Override
  public boolean isHighlighterEnabled(@NotNull String id) {
    VcsLogHighlighterProperty property = VcsLogHighlighterProperty.get(id);
    return myUiProperties.exists(property) && myUiProperties.get(property);
  }

  protected void applyFiltersAndUpdateUi(@NotNull VcsLogFilterCollection filters) {
    myRefresher.onFiltersChange(filters);
    myFilterListenerDispatcher.getMulticaster().onFiltersChanged();

    JComponent toolbar = myMainFrame.getToolbar();
    toolbar.revalidate();
    toolbar.repaint();
  }

  @Override
  public void addFilterListener(@NotNull VcsLogFilterListener listener) {
    myFilterListenerDispatcher.addListener(listener);
  }

  @NotNull
  @Override
  public VcsLogGraphTable getTable() {
    return myMainFrame.getGraphTable();
  }

  @NotNull
  @Override
  public JComponent getMainComponent() {
    return myMainFrame;
  }

  @NotNull
  @Override
  public VcsLogFilterUiEx getFilterUi() {
    return myMainFrame.getFilterUi();
  }

  @Override
  @NotNull
  public JComponent getToolbar() {
    return myMainFrame.getToolbar();
  }

  @Override
  @NotNull
  public MainVcsLogUiProperties getProperties() {
    return myUiProperties;
  }

  @Nullable
  @Override
  public String getHelpId() {
    return HELP_ID;
  }

  @Nullable
  @Override
  public History getNavigationHistory() {
    return myHistory;
  }

  @Override
  public void dispose() {
    myUiProperties.removeChangeListener(myPropertiesListener);
    super.dispose();
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
      else if (CommonUiProperties.SHOW_ROOT_NAMES.equals(property)) {
        getTable().rootColumnUpdated();
      }
      else if (MainVcsLogUiProperties.COMPACT_REFERENCES_VIEW.equals(property)) {
        getTable().setCompactReferencesView(myUiProperties.get(MainVcsLogUiProperties.COMPACT_REFERENCES_VIEW));
      }
      else if (MainVcsLogUiProperties.SHOW_TAG_NAMES.equals(property)) {
        getTable().setShowTagNames(myUiProperties.get(MainVcsLogUiProperties.SHOW_TAG_NAMES));
      }
      else if (MainVcsLogUiProperties.LABELS_LEFT_ALIGNED.equals(property)) {
        getTable().setLabelsLeftAligned(myUiProperties.get(MainVcsLogUiProperties.LABELS_LEFT_ALIGNED));
      }
      else if (MainVcsLogUiProperties.BEK_SORT_TYPE.equals(property)) {
        myRefresher.onSortTypeChange(myUiProperties.get(MainVcsLogUiProperties.BEK_SORT_TYPE));
      }
      else if (CommonUiProperties.COLUMN_ORDER.equals(property)) {
        getTable().onColumnOrderSettingChanged();
      }
      else if (property instanceof VcsLogHighlighterProperty) {
        getTable().repaint();
      }
      else if (property instanceof CommonUiProperties.TableColumnProperty) {
        getTable().forceReLayout(((CommonUiProperties.TableColumnProperty)property).getColumn());
      }
      else if (property.equals(CommonUiProperties.PREFER_COMMIT_DATE) && getTable().getTableColumn(VcsLogColumn.DATE) != null) {
        getTable().repaint();
      }
    }

    private void onShowLongEdgesChanged() {
      myVisiblePack.getVisibleGraph().getActionController()
        .setLongEdgesHidden(!myUiProperties.get(MainVcsLogUiProperties.SHOW_LONG_EDGES));
    }
  }
}
