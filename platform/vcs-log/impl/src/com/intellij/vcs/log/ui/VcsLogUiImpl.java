package com.intellij.vcs.log.ui;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.PairFunction;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogFilterUi;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties.VcsLogHighlighterProperty;
import com.intellij.vcs.log.impl.VcsLogUiPropertiesImpl;
import com.intellij.vcs.log.ui.frame.MainFrame;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class VcsLogUiImpl extends AbstractVcsLogUi {
  @NotNull private final MainVcsLogUiProperties myUiProperties;
  @NotNull private final MainFrame myMainFrame;
  @NotNull private final VcsLogUiPropertiesImpl.MainVcsLogUiPropertiesListener myPropertiesListener;

  public VcsLogUiImpl(@NotNull VcsLogData logData,
                      @NotNull Project project,
                      @NotNull VcsLogColorManager manager,
                      @NotNull MainVcsLogUiProperties uiProperties,
                      @NotNull VisiblePackRefresher refresher) {
    super(logData, project, manager, refresher);
    myUiProperties = uiProperties;
    myMainFrame = new MainFrame(logData, this, project, uiProperties, myLog, myVisiblePack);

    for (VcsLogHighlighterFactory factory : Extensions.getExtensions(LOG_HIGHLIGHTER_FACTORY_EP, myProject)) {
      getTable().addHighlighter(factory.createHighlighter(logData, this));
    }

    myPropertiesListener = new MyVcsLogUiPropertiesListener();
    myUiProperties.addChangeListener(myPropertiesListener);
  }

  protected void onVisiblePackUpdated(boolean permGraphChanged) {
    myMainFrame.updateDataPack(myVisiblePack, permGraphChanged);
    myPropertiesListener.onShowLongEdgesChanged();
  }

  @NotNull
  public MainFrame getMainFrame() {
    return myMainFrame;
  }

  private void performLongAction(@NotNull final GraphAction graphAction, @NotNull final String title) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      final GraphAnswer<Integer> answer = myVisiblePack.getVisibleGraph().getActionController().performAction(graphAction);
      final Runnable updater = answer.getGraphUpdater();
      ApplicationManager.getApplication().invokeLater(() -> {
        assert updater != null : "Action:" +
                                 title +
                                 "\nController: " +
                                 myVisiblePack.getVisibleGraph().getActionController() +
                                 "\nAnswer:" +
                                 answer;
        updater.run();
        getTable().handleAnswer(answer);
      });
    }, title, false, null, getMainFrame().getMainComponent());
  }

  public void expandAll() {
    performLongAction(new GraphAction.GraphActionImpl(null, GraphAction.Type.BUTTON_EXPAND),
                      "Expanding " +
                      (myUiProperties.get(MainVcsLogUiProperties.BEK_SORT_TYPE) == PermanentGraph.SortType.LinearBek
                       ? "merges..."
                       : "linear branches..."));
  }

  public void collapseAll() {
    performLongAction(new GraphAction.GraphActionImpl(null, GraphAction.Type.BUTTON_COLLAPSE),
                      "Collapsing " +
                      (myUiProperties.get(MainVcsLogUiProperties.BEK_SORT_TYPE) == PermanentGraph.SortType.LinearBek
                       ? "merges..."
                       : "linear branches..."));
  }

  @Override
  protected <T> void handleCommitNotFound(@NotNull T commitId, @NotNull PairFunction<GraphTableModel, T, Integer> rowGetter) {
    if (getFilters().isEmpty()) {
      super.handleCommitNotFound(commitId, rowGetter);
    }
    else {
      showWarningWithLink("Commit " + commitId.toString() + " does not exist or does not match active filters",
                          "Reset filters and search again.", () -> {
          getFilterUi().setFilter(null);
          invokeOnChange(() -> jumpTo(commitId, rowGetter, SettableFuture.create()));
        });
    }
  }

  public boolean isShowRootNames() {
    return myUiProperties.get(MainVcsLogUiProperties.SHOW_ROOT_NAMES);
  }

  @Override
  public boolean isHighlighterEnabled(@NotNull String id) {
    VcsLogHighlighterProperty property = VcsLogHighlighterProperty.get(id);
    return myUiProperties.exists(property) && myUiProperties.get(property);
  }

  @Override
  public boolean isMultipleRoots() {
    return myColorManager.isMultipleRoots(); // somewhy color manager knows about this
  }

  public void applyFiltersAndUpdateUi(@NotNull VcsLogFilterCollection filters) {
    myRefresher.onFiltersChange(filters);
  }

  @NotNull
  @Override
  public VcsLogGraphTable getTable() {
    return myMainFrame.getGraphTable();
  }

  @Override
  @NotNull
  protected VcsLogFilterCollection getFilters() {
    return myMainFrame.getFilterUi().getFilters();
  }

  @NotNull
  @Override
  public Component getMainComponent() {
    return myMainFrame.getMainComponent();
  }

  @NotNull
  public JComponent getToolbar() {
    return myMainFrame.getToolbar();
  }

  @NotNull
  @Override
  public VcsLogFilterUi getFilterUi() {
    return myMainFrame.getFilterUi();
  }

  @Override
  @NotNull
  public MainVcsLogUiProperties getProperties() {
    return myUiProperties;
  }

  @Override
  public void dispose() {
    myUiProperties.removeChangeListener(myPropertiesListener);
    super.dispose();
  }

  private class MyVcsLogUiPropertiesListener extends VcsLogUiPropertiesImpl.MainVcsLogUiPropertiesListener {
    @Override
    public void onShowDetailsChanged() {
      myMainFrame.showDetails(myUiProperties.get(CommonUiProperties.SHOW_DETAILS));
    }

    @Override
    public void onShowLongEdgesChanged() {
      myVisiblePack.getVisibleGraph().getActionController().setLongEdgesHidden(!myUiProperties.get(MainVcsLogUiProperties.SHOW_LONG_EDGES));
    }

    @Override
    public void onBekChanged() {
      myRefresher.onSortTypeChange(myUiProperties.get(MainVcsLogUiProperties.BEK_SORT_TYPE));
    }

    @Override
    public void onShowRootNamesChanged() {
      myMainFrame.getGraphTable().rootColumnUpdated();
    }

    @Override
    public void onHighlighterChanged() {
      myMainFrame.getGraphTable().repaint();
    }

    @Override
    public void onCompactReferencesViewChanged() {
      myMainFrame.getGraphTable().setCompactReferencesView(myUiProperties.get(MainVcsLogUiProperties.COMPACT_REFERENCES_VIEW));
    }

    @Override
    public void onShowTagNamesChanged() {
      myMainFrame.getGraphTable().setShowTagNames(myUiProperties.get(MainVcsLogUiProperties.SHOW_TAG_NAMES));
    }

    @Override
    public void onColumnWidthChanged(int column) {
      myMainFrame.getGraphTable().forceReLayout(column);
    }

    @Override
    public void onColumnOrderChanged() {
      myMainFrame.getGraphTable().onColumnOrderSettingChanged();
    }

    @Override
    public void onTextFilterSettingsChanged() {
      applyFiltersAndUpdateUi(myMainFrame.getFilterUi().getFilters());
    }
  }
}
