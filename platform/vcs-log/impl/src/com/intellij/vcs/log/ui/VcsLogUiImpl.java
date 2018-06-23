package com.intellij.vcs.log.ui;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
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
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.frame.MainFrame;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class VcsLogUiImpl extends AbstractVcsLogUi {
  private static final String HELP_ID = "reference.changesToolWindow.log";
  
  @NotNull private final MainVcsLogUiProperties myUiProperties;
  @NotNull private final MainFrame myMainFrame;
  @NotNull private final MyVcsLogUiPropertiesListener myPropertiesListener;

  public VcsLogUiImpl(@NotNull String id,
                      @NotNull VcsLogData logData,
                      @NotNull VcsLogColorManager manager,
                      @NotNull MainVcsLogUiProperties uiProperties,
                      @NotNull VisiblePackRefresher refresher) {
    super(id, logData, manager, refresher);
    myUiProperties = uiProperties;
    myMainFrame = new MainFrame(logData, this, uiProperties, myLog, myVisiblePack);

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
    if (getFilterUi().getFilters().isEmpty()) {
      super.handleCommitNotFound(commitId, rowGetter);
    }
    else {
      showWarningWithLink("Commit " + commitId.toString() + " does not exist or does not match active filters",
                          "Reset filters and search again.", () -> {
          getFilterUi().setFilter(null);
          invokeOnChange(() -> jumpTo(commitId, rowGetter, SettableFuture.create()), pack -> pack.getFilters().isEmpty());
        });
    }
  }

  @Override
  public boolean isHighlighterEnabled(@NotNull String id) {
    VcsLogHighlighterProperty property = VcsLogHighlighterProperty.get(id);
    return myUiProperties.exists(property) && myUiProperties.get(property);
  }

  public void applyFiltersAndUpdateUi(@NotNull VcsLogFilterCollection filters) {
    myRefresher.onFiltersChange(filters);
  }

  @NotNull
  @Override
  public VcsLogGraphTable getTable() {
    return myMainFrame.getGraphTable();
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

  @Nullable
  @Override
  public String getHelpId() {
    return HELP_ID;
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
      else if (CommonUiProperties.SHOW_DIFF_PREVIEW.equals(property)) {
        myMainFrame.showDiffPreview(myUiProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW));
      }
      else if (MainVcsLogUiProperties.SHOW_LONG_EDGES.equals(property)) {
        onShowLongEdgesChanged();
      }
      else if (CommonUiProperties.SHOW_ROOT_NAMES.equals(property)) {
        myMainFrame.getGraphTable().rootColumnUpdated();
      }
      else if (MainVcsLogUiProperties.COMPACT_REFERENCES_VIEW.equals(property)) {
        myMainFrame.getGraphTable().setCompactReferencesView(myUiProperties.get(MainVcsLogUiProperties.COMPACT_REFERENCES_VIEW));
      }
      else if (MainVcsLogUiProperties.SHOW_TAG_NAMES.equals(property)) {
        myMainFrame.getGraphTable().setShowTagNames(myUiProperties.get(MainVcsLogUiProperties.SHOW_TAG_NAMES));
      }
      else if (MainVcsLogUiProperties.BEK_SORT_TYPE.equals(property)) {
        myRefresher.onSortTypeChange(myUiProperties.get(MainVcsLogUiProperties.BEK_SORT_TYPE));
      }
      else if (MainVcsLogUiProperties.TEXT_FILTER_REGEX.equals(property) ||
               MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE.equals(property)) {
        applyFiltersAndUpdateUi(myMainFrame.getFilterUi().getFilters());
      }
      else if (CommonUiProperties.COLUMN_ORDER.equals(property)) {
        myMainFrame.getGraphTable().onColumnOrderSettingChanged();
      }
      else if (property instanceof VcsLogHighlighterProperty) {
        myMainFrame.getGraphTable().repaint();
      }
      else if (property instanceof CommonUiProperties.TableColumnProperty) {
        myMainFrame.getGraphTable().forceReLayout(((CommonUiProperties.TableColumnProperty)property).getColumn());
      }
      else if (MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS.equals(property)) {
      }
      else {
        throw new UnsupportedOperationException("Property " + property + " does not exist");
      }
    }

    private void onShowLongEdgesChanged() {
      myVisiblePack.getVisibleGraph().getActionController()
                   .setLongEdgesHidden(!myUiProperties.get(MainVcsLogUiProperties.SHOW_LONG_EDGES));
    }
  }
}
