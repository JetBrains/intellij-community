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
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties.VcsLogHighlighterProperty;
import com.intellij.vcs.log.impl.VcsLogImpl;
import com.intellij.vcs.log.impl.VcsLogUiPropertiesImpl;
import com.intellij.vcs.log.ui.frame.MainFrame;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.VisiblePackChangeListener;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
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
  @NotNull private final MainVcsLogUiProperties myUiProperties;
  @NotNull private final VisiblePackRefresher myRefresher;

  @NotNull private final Collection<VcsLogListener> myLogListeners = ContainerUtil.newArrayList();
  @NotNull private final VisiblePackChangeListener myVisiblePackChangeListener;
  @NotNull private final VcsLogUiPropertiesImpl.MainVcsLogUiPropertiesListener myPropertiesListener;

  @NotNull private VisiblePack myVisiblePack;

  public VcsLogUiImpl(@NotNull VcsLogData logData,
                      @NotNull Project project,
                      @NotNull VcsLogColorManager manager,
                      @NotNull MainVcsLogUiProperties uiProperties,
                      @NotNull VisiblePackRefresher refresher) {
    myProject = project;
    myColorManager = manager;
    myUiProperties = uiProperties;
    Disposer.register(logData, this);

    myRefresher = refresher;
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
    myRefresher.addVisiblePackChangeListener(myVisiblePackChangeListener);

    myPropertiesListener = new MyVcsLogUiPropertiesListener();
    myUiProperties.addChangeListener(myPropertiesListener);
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
    myPropertiesListener.onShowLongEdgesChanged();
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
        getTable().handleAnswer(answer, true);
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

  public boolean isShowRootNames() {
    return myUiProperties.get(MainVcsLogUiProperties.SHOW_ROOT_NAMES);
  }

  @Override
  public boolean isHighlighterEnabled(@NotNull String id) {
    VcsLogHighlighterProperty property = VcsLogHighlighterProperty.get(id);
    return myUiProperties.exists(property) && myUiProperties.get(property);
  }

  @Override
  public boolean areGraphActionsEnabled() {
    return myMainFrame.areGraphActionsEnabled();
  }

  public boolean isCompactReferencesView() {
    return myUiProperties.get(MainVcsLogUiProperties.COMPACT_REFERENCES_VIEW);
  }

  public boolean isShowTagNames() {
    return myUiProperties.get(MainVcsLogUiProperties.SHOW_TAG_NAMES);
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

  public void applyFiltersAndUpdateUi(@NotNull VcsLogFilterCollection filters) {
    myRefresher.onFiltersChange(filters);
  }

  @NotNull
  public VisiblePackRefresher getRefresher() {
    return myRefresher;
  }

  @NotNull
  public VcsLogGraphTable getTable() {
    return myMainFrame.getGraphTable();
  }

  @NotNull
  public Project getProject() {
    return myProject;
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
    myUiProperties.removeChangeListener(myPropertiesListener);
    myRefresher.removeVisiblePackChangeListener(myVisiblePackChangeListener);
    getTable().removeAllHighlighters();
    myVisiblePack = VisiblePack.EMPTY;
  }

  public MainVcsLogUiProperties getProperties() {
    return myUiProperties;
  }

  private class MyVcsLogUiPropertiesListener extends VcsLogUiPropertiesImpl.MainVcsLogUiPropertiesListener {
    @Override
    public void onShowDetailsChanged() {
      myMainFrame.showDetails(myUiProperties.get(MainVcsLogUiProperties.SHOW_DETAILS));
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
      repaintUI();
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
    public void onTextFilterSettingsChanged() {
      applyFiltersAndUpdateUi(myMainFrame.getFilterUi().getFilters());
    }
  }
}
