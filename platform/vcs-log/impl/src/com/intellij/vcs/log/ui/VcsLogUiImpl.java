package com.intellij.vcs.log.ui;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.*;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.impl.VcsLogImpl;
import com.intellij.vcs.log.ui.frame.MainFrame;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Future;

public class VcsLogUiImpl implements VcsLogUi, Disposable {
  private static final Logger LOG = Logger.getInstance(VcsLogUiImpl.class);
  public static final ExtensionPointName<VcsLogHighlighterFactory> LOG_HIGHLIGHTER_FACTORY_EP =
    ExtensionPointName.create("com.intellij.logHighlighterFactory");

  @NotNull private final MainFrame myMainFrame;
  @NotNull private final Project myProject;
  @NotNull private final VcsLogColorManager myColorManager;
  @NotNull private final VcsLog myLog;
  @NotNull private final VcsLogUiProperties myUiProperties;
  @NotNull private final VcsLogFilterer myFilterer;

  @NotNull private final Collection<VcsLogListener> myLogListeners = ContainerUtil.newArrayList();
  private final VisiblePackChangeListener myVisiblePackChangeListener;

  @NotNull private VisiblePack myVisiblePack;

  public VcsLogUiImpl(@NotNull VcsLogData logData,
                      @NotNull Project project,
                      @NotNull VcsLogColorManager manager,
                      @NotNull VcsLogUiProperties uiProperties,
                      @NotNull VcsLogFilterer filterer) {
    myProject = project;
    myColorManager = manager;
    myUiProperties = uiProperties;
    Disposer.register(logData, this);

    myFilterer = filterer;
    myLog = new VcsLogImpl(logData, this);
    myVisiblePack = VisiblePack.EMPTY;
    myMainFrame = new MainFrame(logData, this, project, uiProperties, myLog, myVisiblePack);

    for (VcsLogHighlighterFactory factory : Extensions.getExtensions(LOG_HIGHLIGHTER_FACTORY_EP, myProject)) {
      getTable().addHighlighter(factory.createHighlighter(logData, this));
    }

    myVisiblePackChangeListener = visiblePack -> UIUtil.invokeLaterIfNeeded(() -> {
      if (!Disposer.isDisposed(this)) {
        setVisiblePack(visiblePack);
      }
    });
    myFilterer.addVisiblePackChangeListener(myVisiblePackChangeListener);
  }

  public void requestFocus() {
    // todo fix selection
    final VcsLogGraphTable graphTable = myMainFrame.getGraphTable();
    if (graphTable.getRowCount() > 0) {
      IdeFocusManager.getInstance(myProject).requestFocus(graphTable, true).doWhenProcessed(() -> graphTable.setRowSelectionInterval(0, 0));
    }
  }

  public void setVisiblePack(@NotNull VisiblePack pack) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    boolean permGraphChanged = myVisiblePack.getDataPack() != pack.getDataPack();

    myVisiblePack = pack;

    myMainFrame.updateDataPack(myVisiblePack, permGraphChanged);
    setLongEdgeVisibility(myUiProperties.areLongEdgesVisible());
    fireFilterChangeEvent(myVisiblePack, permGraphChanged);
    repaintUI();
  }

  @NotNull
  public MainFrame getMainFrame() {
    return myMainFrame;
  }

  public void repaintUI() {
    myMainFrame.getGraphTable().repaint();
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
        getTable().handleAnswer(answer, true, null, null);
      });
    }, title, false, null, getMainFrame().getMainComponent());
  }

  public void expandAll() {
    performLongAction(new GraphAction.GraphActionImpl(null, GraphAction.Type.BUTTON_EXPAND),
                      "Expanding " + (getBekType() == PermanentGraph.SortType.LinearBek ? "merges..." : "linear branches..."));
  }

  public void collapseAll() {
    performLongAction(new GraphAction.GraphActionImpl(null, GraphAction.Type.BUTTON_COLLAPSE),
                      "Collapsing " + (getBekType() == PermanentGraph.SortType.LinearBek ? "merges..." : "linear branches..."));
  }

  @Override
  public void setLongEdgeVisibility(boolean visibility) {
    myVisiblePack.getVisibleGraph().getActionController().setLongEdgesHidden(!visibility);
    myUiProperties.setLongEdgesVisibility(visibility);
  }

  @Override
  public boolean areLongEdgesVisible() {
    return myUiProperties.areLongEdgesVisible();
  }

  @Override
  public void setBekType(@NotNull PermanentGraph.SortType bekType) {
    myUiProperties.setBek(bekType.ordinal());
    myFilterer.onSortTypeChange(bekType);
  }

  @Override
  @NotNull
  public PermanentGraph.SortType getBekType() {
    return PermanentGraph.SortType.values()[myUiProperties.getBekSortType()];
  }

  public void setShowRootNames(boolean isShowRootNames) {
    myUiProperties.setShowRootNames(isShowRootNames);
    myMainFrame.getGraphTable().rootColumnUpdated();
  }

  public boolean isShowRootNames() {
    return myUiProperties.isShowRootNames();
  }

  @Override
  public boolean isHighlighterEnabled(@NotNull String id) {
    return myUiProperties.isHighlighterEnabled(id);
  }

  @Override
  public void setHighlighterEnabled(@NotNull String id, boolean enabled) {
    myUiProperties.enableHighlighter(id, enabled);
    repaintUI();
  }

  @Override
  public boolean areGraphActionsEnabled() {
    return myMainFrame.areGraphActionsEnabled();
  }

  @Override
  public boolean isShowDetails() {
    return myUiProperties.isShowDetails();
  }

  @Override
  public void setShowDetails(boolean showDetails) {
    myMainFrame.showDetails(showDetails);
    myUiProperties.setShowDetails(showDetails);
  }

  @NotNull
  public Future<Boolean> jumpToCommit(@NotNull Hash commitHash, @NotNull VirtualFile root) {
    SettableFuture<Boolean> future = SettableFuture.create();
    jumpToCommit(commitHash, root, future);
    return future;
  }

  public void jumpToCommit(@NotNull Hash commitHash, @NotNull VirtualFile root, @NotNull SettableFuture<Boolean> future) {
    jumpTo(commitHash, (model, hash) -> model.getRowOfCommit(hash, root), future);
  }

  public void jumpToCommitByPartOfHash(@NotNull String commitHash, @NotNull SettableFuture<Boolean> future) {
    jumpTo(commitHash, GraphTableModel::getRowOfCommitByPartOfHash, future);
  }

  private <T> void jumpTo(@NotNull final T commitId,
                          @NotNull final PairFunction<GraphTableModel, T, Integer> rowGetter,
                          @NotNull final SettableFuture<Boolean> future) {
    if (future.isCancelled()) return;

    GraphTableModel model = getTable().getModel();

    int row = rowGetter.fun(model, commitId);
    if (row >= 0) {
      myMainFrame.getGraphTable().jumpToRow(row);
      future.set(true);
    }
    else if (model.canRequestMore()) {
      model.requestToLoadMore(() -> jumpTo(commitId, rowGetter, future));
    }
    else if (!myVisiblePack.isFull()) {
      invokeOnChange(() -> jumpTo(commitId, rowGetter, future));
    }
    else {
      commitNotFound(commitId.toString());
      future.set(false);
    }
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

  public void applyFiltersAndUpdateUi() {
    VcsLogFilterCollection filters = myMainFrame.getFilterUi().getFilters();
    myFilterer.onFiltersChange(filters);
    myMainFrame.onFiltersChange(filters);
  }

  @NotNull
  public VcsLogFilterer getFilterer() {
    return myFilterer;
  }

  @NotNull
  public VcsLogGraphTable getTable() {
    return myMainFrame.getGraphTable();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public void setBranchesPanelVisible(boolean visible) {
    myMainFrame.setBranchesPanelVisible(visible);
    myUiProperties.setShowBranchesPanel(visible);
  }

  @Override
  public boolean isBranchesPanelVisible() {
    return myUiProperties.isShowBranchesPanel();
  }

  @NotNull
  public JComponent getToolbar() {
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
    Collection<VcsLogListener> logListeners = new ArrayList<>(myLogListeners);

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
    myFilterer.removeVisiblePackChangeListener(myVisiblePackChangeListener);
    getTable().removeAllHighlighters();
    myVisiblePack = VisiblePack.EMPTY;
  }
}
