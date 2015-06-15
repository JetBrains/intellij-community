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
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogFilterer;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.impl.VcsLogImpl;
import com.intellij.vcs.log.ui.frame.MainFrame;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Future;

public class VcsLogUiImpl implements VcsLogUi, Disposable {
  public static final ExtensionPointName<VcsLogHighlighterFactory> LOG_HIGHLIGHTER_FACTORY_EP =
    ExtensionPointName.create("com.intellij.logHighlighterFactory");

  public static final String POPUP_ACTION_GROUP = "Vcs.Log.ContextMenu";
  public static final String TOOLBAR_ACTION_GROUP = "Vcs.Log.Toolbar";
  public static final String VCS_LOG_TABLE_PLACE = "Vcs.Log.ContextMenu";
  public static final String VCS_LOG_INTELLI_SORT_ACTION = "Vcs.Log.IntelliSortChooser";

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

    for (VcsLogHighlighterFactory factory : Extensions.getExtensions(LOG_HIGHLIGHTER_FACTORY_EP, myProject)) {
      addHighlighter(factory.createHighlighter(myLogDataHolder, myUiProperties));
    }
  }

  public void setVisiblePack(@NotNull VisiblePack pack) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    boolean permGraphChanged = myVisiblePack.getPermanentGraph() != pack.getPermanentGraph();

    myVisiblePack = pack;

    myMainFrame.updateDataPack(myVisiblePack);
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
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        final GraphAnswer<Integer> answer = myVisiblePack.getVisibleGraph().getActionController().performAction(graphAction);
        final Runnable updater = answer.getGraphUpdater();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            assert updater != null : "Action:" +
                                     title +
                                     "\nController: " +
                                     myVisiblePack.getVisibleGraph().getActionController() +
                                     "\nAnswer:" +
                                     answer;
            updater.run();
            getTable().handleAnswer(answer, true, null);
          }
        });
      }
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

  public void setLongEdgeVisibility(boolean visibility) {
    myVisiblePack.getVisibleGraph().getActionController().setLongEdgesHidden(!visibility);
    myUiProperties.setLongEdgesVisibility(visibility);
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
    myVisiblePack = VisiblePack.EMPTY;
  }
}
