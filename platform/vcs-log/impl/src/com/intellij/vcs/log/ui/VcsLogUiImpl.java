package com.intellij.vcs.log.ui;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogFilterer;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.impl.VcsLogImpl;
import com.intellij.vcs.log.ui.frame.MainFrame;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Future;

public class VcsLogUiImpl implements VcsLogUi, Disposable {

  public static final String POPUP_ACTION_GROUP = "Vcs.Log.ContextMenu";
  public static final String TOOLBAR_ACTION_GROUP = "Vcs.Log.Toolbar";
  public static final String VCS_LOG_TABLE_PLACE = "Vcs.Log.ContextMenu";

  private static final Logger LOG = Logger.getInstance(VcsLogUiImpl.class);

  @NotNull private final MainFrame myMainFrame;
  @NotNull private final VcsLogDataHolder myLogDataHolder;
  @NotNull private final Project myProject;
  @NotNull private final VcsLogColorManager myColorManager;
  @NotNull private final VcsLog myLog;
  @NotNull private final VcsLogUiProperties myUiProperties;
  @NotNull private final VcsLogFilterer myFilterer;

  @NotNull private final Collection<VcsLogListener> myLogListeners = ContainerUtil.newArrayList();

  @NotNull private VisiblePack myVisiblePack;

  public VcsLogUiImpl(@NotNull VcsLogDataHolder logDataHolder,
                      @NotNull Project project,
                      @NotNull VcsLogSettings settings,
                      @NotNull VcsLogColorManager manager,
                      @NotNull VcsLogUiProperties uiProperties,
                      @NotNull VcsLogFilterer filterer) {
    myLogDataHolder = logDataHolder;
    myProject = project;
    myColorManager = manager;
    myUiProperties = uiProperties;
    Disposer.register(logDataHolder, this);

    myFilterer = filterer;
    myLog = new VcsLogImpl(logDataHolder, this);
    myVisiblePack = VisiblePack.EMPTY;
    myMainFrame = new MainFrame(logDataHolder, this, project, settings, uiProperties, myLog, myVisiblePack);
  }

  public void setVisiblePack(@NotNull VisiblePack pack) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    PermanentGraph<Integer> previousPermGraph = myVisiblePack.getPermanentGraph();
    TIntHashSet previouslySelected = getSelectedCommits();

    myVisiblePack = pack;
    boolean permGraphChanged = previousPermGraph != myVisiblePack.getPermanentGraph();

    GraphTableModel currentModel = getModel();
    if (currentModel == null) {
      getTable().setModel(new GraphTableModel(myVisiblePack, myLogDataHolder, this));
    }
    else {
      currentModel.setVisiblePack(myVisiblePack);
      restoreSelection(currentModel, myVisiblePack.getVisibleGraph(), previouslySelected, getTable());
    }
    getTable().setPaintBusy(false);

    myMainFrame.updateDataPack(myVisiblePack);
    setLongEdgeVisibility(myUiProperties.areLongEdgesVisible());
    fireFilterChangeEvent(myVisiblePack, permGraphChanged);
    repaintUI();
  }

  @NotNull
  public MainFrame getMainFrame() {
    return myMainFrame;
  }

  private static void restoreSelection(@NotNull GraphTableModel newModel,
                                       @NotNull VisibleGraph<Integer> newVisibleGraph,
                                       @NotNull TIntHashSet previouslySelectedCommits,
                                       @NotNull final VcsLogGraphTable table) {
    TIntHashSet rowsToSelect = findNewRowsToSelect(newModel, newVisibleGraph, previouslySelectedCommits);
    rowsToSelect.forEach(new TIntProcedure() {
      @Override
      public boolean execute(int row) {
        table.addRowSelectionInterval(row, row);
        return true;
      }
    });
  }

  @NotNull
  private static TIntHashSet findNewRowsToSelect(@NotNull GraphTableModel newModel,
                                                 @NotNull VisibleGraph<Integer> visibleGraph,
                                                 @NotNull TIntHashSet selectedHashes) {
    TIntHashSet rowsToSelect = new TIntHashSet();
    if (newModel.getRowCount() == 0) {
      // this should have been covered by facade.getVisibleCommitCount,
      // but if the table is empty (no commits match the filter), the GraphFacade is not updated, because it can't handle it
      // => it has previous values set.
      return rowsToSelect;
    }
    for (int row = 0;
         row < visibleGraph.getVisibleCommitCount() && rowsToSelect.size() < selectedHashes.size();
         row++) { //stop iterating if found all hashes
      int commit = visibleGraph.getRowInfo(row).getCommit();
      if (selectedHashes.contains(commit)) {
        rowsToSelect.add(row);
      }
    }
    return rowsToSelect;
  }

  public void repaintUI() {
    myMainFrame.getGraphTable().repaint();
  }

  public void showAll() {
    runUnderModalProgress("Expanding linear branches...", new Runnable() {
      @Override
      public void run() {
        myVisiblePack.getVisibleGraph().getActionController().setLinearBranchesExpansion(false);
      }
    });
    handleAnswer(null, true);
  }

  public void hideAll() {
    runUnderModalProgress("Collapsing linear branches...", new Runnable() {
      @Override
      public void run() {
        myVisiblePack.getVisibleGraph().getActionController().setLinearBranchesExpansion(true);
      }
    });
    handleAnswer(null, true);
  }

  public void setLongEdgeVisibility(boolean visibility) {
    myVisiblePack.getVisibleGraph().getActionController().setLongEdgesHidden(!visibility);
    myUiProperties.setLongEdgesVisibility(visibility);
  }

  public void setBek(boolean bek) {
    myUiProperties.setBek(bek);
    myFilterer.onSortTypeChange(bek ? PermanentGraph.SortType.Bek : PermanentGraph.SortType.Normal);
  }

  public boolean isBek() {
    return myUiProperties.isBek();
  }

  public void setShowRootNames(boolean isShowRootNames) {
    myUiProperties.setShowRootNames(isShowRootNames);
    myMainFrame.getGraphTable().rootColumnUpdated();
  }

  public boolean isShowRootNames() {
    return myUiProperties.isShowRootNames();
  }

  @NotNull
  public Future<Boolean> jumpToCommit(@NotNull Hash commitHash) {
    SettableFuture<Boolean> future = SettableFuture.create();
    jumpTo(commitHash, new PairFunction<GraphTableModel, Hash, Integer>() {
      @Override
      public Integer fun(GraphTableModel model, Hash hash) {
        return model.getRowOfCommit(hash);
      }
    }, future);
    return future;
  }

  @NotNull
  public Future<Boolean> jumpToCommitByPartOfHash(@NotNull String commitHash) {
    SettableFuture<Boolean> future = SettableFuture.create();
    jumpTo(commitHash, new PairFunction<GraphTableModel, String, Integer>() {
      @Override
      public Integer fun(GraphTableModel model, String hash) {
        return model.getRowOfCommitByPartOfHash(hash);
      }
    }, future);
    return future;
  }

  public void handleAnswer(@Nullable GraphAnswer<Integer> answer, boolean dataCouldChange) {
    if (dataCouldChange) {
      ((AbstractTableModel)(getTable().getModel())).fireTableDataChanged();
    }

    repaintUI();

    if (answer == null) {
      return;
    }

    if (answer.getCursorToSet() != null) {
      myMainFrame.getGraphTable().setCursor(answer.getCursorToSet());
    }
    if (answer.getCommitToJump() != null) {
      int row = myVisiblePack.getVisibleGraph().getVisibleRowIndex(answer.getCommitToJump());
      if (row >= 0) {
        myMainFrame.getGraphTable().jumpToRow(row);
      }
      else {
        // TODO wait for the full log and then jump
      }
    }
  }

  private <T> void jumpTo(@NotNull final T commitId,
                          @NotNull final PairFunction<GraphTableModel, T, Integer> rowGetter,
                          @NotNull final SettableFuture<Boolean> future) {
    if (future.isCancelled()) return;

    GraphTableModel model = getModel();
    if (model == null) {
      invokeOnChange(new Runnable() {
        @Override
        public void run() {
          jumpTo(commitId, rowGetter, future);
        }
      });
      return;
    }

    int row = rowGetter.fun(model, commitId);
    if (row >= 0) {
      myMainFrame.getGraphTable().jumpToRow(row);
      future.set(true);
    }
    else if (model.canRequestMore()) {
      model.requestToLoadMore(new Runnable() {
        @Override
        public void run() {
          jumpTo(commitId, rowGetter, future);
        }
      });
    }
    else if (!myVisiblePack.isFull()) {
      invokeOnChange(new Runnable() {
        @Override
        public void run() {
          jumpTo(commitId, rowGetter, future);
        }
      });
    }
    else {
      commitNotFound(commitId.toString());
      future.set(false);
    }
  }

  @Nullable
  private GraphTableModel getModel() {
    TableModel model = getTable().getModel();
    if (model instanceof GraphTableModel) {
      return (GraphTableModel)model;
    }
    return null;
  }

  private void showMessage(@NotNull MessageType messageType, @NotNull String message) {
    LOG.info(message);
    VcsBalloonProblemNotifier.showOverChangesView(myProject, message, messageType);
  }

  private void commitNotFound(@NotNull String commitHash) {
    if (myMainFrame.getFilterUi().getFilters().isEmpty()) {
      showMessage(MessageType.WARNING, "Commit " + commitHash + " not found");
    }
    else {
      showMessage(MessageType.WARNING, "Commit " + commitHash + " doesn't exist or doesn't match the active filters");
    }
  }

  @Override
  public boolean isMultipleRoots() {
    return myColorManager.isMultipleRoots(); // somewhy color manager knows about this
  }

  @NotNull
  public VcsLogColorManager getColorManager() {
    return myColorManager;
  }

  @NotNull
  public TIntHashSet getSelectedCommits() {
    int[] selectedRows = getTable().getSelectedRows();
    return getCommitsAtRows(myVisiblePack.getVisibleGraph(), selectedRows);
  }

  @NotNull
  private static TIntHashSet getCommitsAtRows(@NotNull VisibleGraph<Integer> graph, int[] rows) {
    TIntHashSet commits = new TIntHashSet();
    for (int row : rows) {
      if (row < graph.getVisibleCommitCount()) {
        commits.add(graph.getRowInfo(row).getCommit());
      }
    }
    return commits;
  }

  public void applyFiltersAndUpdateUi() {
    VcsLogFilterCollection filters = myMainFrame.getFilterUi().getFilters();
    myFilterer.onFiltersChange(filters);
    myMainFrame.onFiltersChange(filters);
  }

  @NotNull
  public VcsLogFilterer getFilterer() {
    return myFilterer;
  }

  public VcsLogGraphTable getTable() {
    return myMainFrame.getGraphTable();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public void runUnderModalProgress(@NotNull final String task, @NotNull final Runnable runnable) {
    getTable().executeWithoutRepaint(new Runnable() {
      public void run() {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, task, false, null, getMainFrame().getMainComponent());
      }
    });
  }

  @Override
  public void setBranchesPanelVisible(boolean visible) {
    myMainFrame.setBranchesPanelVisible(visible);
  }

  public Component getToolbar() {
    return myMainFrame.getToolbar();
  }

  @NotNull
  public VcsLog getVcsLog() {
    return myLog;
  }

  @NotNull
  @Override
  public VcsLogFilterUi getFilterUi() {
    return myMainFrame.getFilterUi();
  }

  @Override
  @NotNull
  public VisiblePack getDataPack() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myVisiblePack;
  }

  @Override
  public void addHighlighter(@NotNull VcsLogHighlighter highlighter) {
    getTable().addHighlighter(highlighter);
    repaintUI();
  }

  @Override
  public void removeHighlighter(@NotNull VcsLogHighlighter highlighter) {
    getTable().removeHighlighter(highlighter);
    repaintUI();
  }

  @Override
  public void addLogListener(@NotNull VcsLogListener listener) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myLogListeners.add(listener);
  }

  @Override
  public void removeLogListener(@NotNull VcsLogListener listener) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myLogListeners.remove(listener);
  }

  private void fireFilterChangeEvent(@NotNull VisiblePack visiblePack, boolean refresh) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Collection<VcsLogListener> logListeners = new ArrayList<VcsLogListener>(myLogListeners);

    for (VcsLogListener listener : logListeners) {
      listener.onChange(visiblePack, refresh);
    }
  }

  public void invokeOnChange(@NotNull final Runnable runnable) {
    addLogListener(new VcsLogListener() {
      @Override
      public void onChange(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
        runnable.run();
        removeLogListener(this);
      }
    });
  }

  @Override
  public void dispose() {
    getTable().removeAllHighlighters();
  }

}
