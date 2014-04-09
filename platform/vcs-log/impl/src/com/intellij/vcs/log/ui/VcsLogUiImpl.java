package com.intellij.vcs.log.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.*;
import com.intellij.vcs.log.graph.ChangeCursorActionRequest;
import com.intellij.vcs.log.graph.ClickGraphAction;
import com.intellij.vcs.log.graph.*;
import com.intellij.vcs.log.impl.VcsLogImpl;
import com.intellij.vcs.log.ui.frame.MainFrame;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import com.intellij.vcs.log.ui.tables.AbstractVcsLogTableModel;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.Collection;

public class VcsLogUiImpl implements VcsLogUi, Disposable {

  public static final String POPUP_ACTION_GROUP = "Vcs.Log.ContextMenu";
  public static final String TOOLBAR_ACTION_GROUP = "Vcs.Log.Toolbar";
  public static final String VCS_LOG_TABLE_PLACE = "Vcs.Log.ContextMenu";

  private static final Logger LOG = Logger.getInstance(VcsLogUiImpl.class);

  @NotNull private final VcsLogDataHolder myLogDataHolder;
  @NotNull private final MainFrame myMainFrame;
  @NotNull private final Project myProject;
  @NotNull private final VcsLogColorManager myColorManager;
  @NotNull private final VcsLogFilterer myFilterer;
  @NotNull private final VcsLog myLog;
  @NotNull private final VcsLogUiProperties myUiProperties;

  @NotNull private final Collection<VcsLogFilterChangeListener> myFilterChangeListeners = ContainerUtil.newArrayList();

  @NotNull private DataPack myDataPack;

  public VcsLogUiImpl(@NotNull VcsLogDataHolder logDataHolder, @NotNull Project project, @NotNull VcsLogSettings settings,
                      @NotNull VcsLogColorManager manager, @NotNull VcsLogUiProperties uiProperties, @NotNull DataPack initialDataPack) {
    myLogDataHolder = logDataHolder;
    myProject = project;
    myColorManager = manager;
    myUiProperties = uiProperties;
    myDataPack = initialDataPack;
    Disposer.register(logDataHolder, this);

    myFilterer = new VcsLogFilterer(logDataHolder, this);
    myLog = new VcsLogImpl(myLogDataHolder, this);
    myMainFrame = new MainFrame(myLogDataHolder, this, project, settings, uiProperties, myLog, initialDataPack);
    project.getMessageBus().connect(project).subscribe(VcsLogDataHolder.REFRESH_COMPLETED, new VcsLogRefreshListener() {
      @Override
      public void refresh(@NotNull DataPack dataPack) {
        applyFiltersAndUpdateUi(dataPack);
      }
    });
    applyFiltersAndUpdateUi(initialDataPack);
  }

  @NotNull
  public MainFrame getMainFrame() {
    return myMainFrame;
  }

  public void jumpToRow(final int rowIndex) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myMainFrame.getGraphTable().jumpToRow(rowIndex);
      }
    });
  }

  public void setModel(@NotNull AbstractVcsLogTableModel newModel, @NotNull DataPack newDataPack,
                       @NotNull TIntHashSet previouslySelectedCommits) {
    final VcsLogGraphTable table = getTable();
    table.setModel(newModel);
    restoreSelection(newModel, newDataPack, previouslySelectedCommits, table);
    table.setPaintBusy(false);
  }

  private static void restoreSelection(@NotNull AbstractVcsLogTableModel newModel,
                                       @NotNull DataPack newDataPack,
                                       @NotNull TIntHashSet previouslySelectedCommits,
                                       @NotNull final VcsLogGraphTable table) {
    TIntHashSet rowsToSelect = findNewRowsToSelect(newModel, newDataPack, previouslySelectedCommits);
    rowsToSelect.forEach(new TIntProcedure() {
      @Override
      public boolean execute(int row) {
        table.addRowSelectionInterval(row, row);
        return true;
      }
    });
  }

  @NotNull
  private static TIntHashSet findNewRowsToSelect(@NotNull AbstractVcsLogTableModel newModel,
                                                 @NotNull DataPack dataPack,
                                                 @NotNull TIntHashSet selectedHashes) {
    TIntHashSet rowsToSelect = new TIntHashSet();
    if (newModel.getRowCount() == 0) {
      // this should have been covered by facade.getVisibleCommitCount,
      // but if the table is empty (no commits match the filter), the GraphFacade is not updated, because it can't handle it
      // => it has previous values set.
      return rowsToSelect;
    }
    GraphFacade facade = dataPack.getGraphFacade();
    for (int row = 0; row < facade.getVisibleCommitCount()
                      && rowsToSelect.size() < selectedHashes.size(); row++) { //stop iterating if found all hashes
      int commit = facade.getCommitAtRow(row);
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
        final GraphAnswer answer = myDataPack.getGraphFacade().performAction(LinearBranchesExpansionAction.EXPAND);
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            handleAnswer(answer);
            jumpToRow(0);
          }
        });
      }
    });
  }

  public void hideAll() {
    runUnderModalProgress("Collapsing linear branches...", new Runnable() {
      @Override
      public void run() {
        final GraphAnswer answer = myDataPack.getGraphFacade().performAction(LinearBranchesExpansionAction.COLLAPSE);
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            handleAnswer(answer);
            jumpToRow(0);
          }
        });
      }
    });
  }

  public void setLongEdgeVisibility(boolean visibility) {
    handleAnswer(myDataPack.getGraphFacade().performAction(LongEdgesAction.valueOf(visibility)));
    myUiProperties.setLongEdgesVisibility(visibility);
  }

  public boolean areLongEdgesHidden() {
    return myDataPack.getGraphFacade().getInfoProvider().areLongEdgesHidden();
  }

  public void click(int rowIndex) {
    handleAnswer(myDataPack.getGraphFacade().performAction(new ClickGraphAction(rowIndex, null)));
  }

  public void jumpToCommit(@NotNull Hash commitHash) {
    jumpTo(commitHash, new PairFunction<AbstractVcsLogTableModel, Hash, Integer>() {
      @Override
      public Integer fun(AbstractVcsLogTableModel model, Hash hash) {
        return model.getRowOfCommit(hash);
      }
    });
  }

  public void jumpToCommitByPartOfHash(@NotNull String commitHash) {
    jumpTo(commitHash, new PairFunction<AbstractVcsLogTableModel, String, Integer>() {
      @Override
      public Integer fun(AbstractVcsLogTableModel model, String hash) {
        return model.getRowOfCommitByPartOfHash(hash);
      }
    });
  }

  public void handleAnswer(@Nullable GraphAnswer answer) {
    repaintUI();

    if (answer == null) {
      return;
    }
    GraphChange graphChange = answer.getGraphChange();
    if (graphChange != null) {
      ((AbstractTableModel)(getTable().getModel())).fireTableStructureChanged();
    }

    GraphActionRequest actionRequest = answer.getActionRequest();
    if (actionRequest instanceof JumpToRowActionRequest) {
      int row = ((JumpToRowActionRequest)actionRequest).getRow();
      jumpToRow(row);
    }
    else if (actionRequest instanceof ChangeCursorActionRequest) {
      myMainFrame.getGraphTable().setCursor(((ChangeCursorActionRequest)actionRequest).getCursor());
    }
  }

  private <T> void jumpTo(@NotNull final T commitId, @NotNull final PairFunction<AbstractVcsLogTableModel, T, Integer> rowGetter) {
    AbstractVcsLogTableModel model = getModel();
    if (model == null) {
      return;
    }

    int row = rowGetter.fun(model, commitId);
    if (row >= 0) {
      jumpToRow(row);
    }
    else if (model.canRequestMore()) {
      model.requestToLoadMore(new Runnable() {
        @Override
        public void run() {
          jumpTo(commitId, rowGetter);
        }
      });
    }
    else {
      commitNotFound(commitId.toString());
    }
  }

  @Nullable
  private AbstractVcsLogTableModel getModel() {
    TableModel model = getTable().getModel();
    if (model instanceof AbstractVcsLogTableModel) {
      return (AbstractVcsLogTableModel)model;
    }
    showMessage(MessageType.WARNING, "The log is not ready to search yet");
    return null;
  }

  private void showMessage(@NotNull MessageType messageType, @NotNull String message) {
    LOG.info(message);
    VcsBalloonProblemNotifier.showOverChangesView(myProject, message, messageType);
  }

  private void commitNotFound(@NotNull String commitHash) {
    if (getFilters().isEmpty()) {
      showMessage(MessageType.WARNING, "Commit " + commitHash + " not found");
    }
    else {
      showMessage(MessageType.WARNING, "Commit " + commitHash + " doesn't exist or doesn't match the active filters");
    }
  }

  @NotNull
  public VcsLogColorManager getColorManager() {
    return myColorManager;
  }

  @NotNull
  public VcsLogFilterer getFilterer() {
    return myFilterer;
  }

  @NotNull 
  public TIntHashSet getSelectedCommits() {
    int[] selectedRows = getTable().getSelectedRows();
    return getCommitsAtRows(myDataPack.getGraphFacade(), selectedRows);
  } 
  
  @NotNull
  private static TIntHashSet getCommitsAtRows(@NotNull GraphFacade facade, int[] rows) {
    TIntHashSet commits = new TIntHashSet();
    for (int row : rows) {
      int commit = facade.getCommitAtRow(row);
      if (commit > 0) {
        commits.add(commit);
      }
    }
    return commits;
  }

  
  private void applyFiltersAndUpdateUi(@NotNull final DataPack dataPack) {
    runUnderModalProgress("Applying filters...", new Runnable() {
      public void run() {
        final TIntHashSet previouslySelected = getSelectedCommits();
        final AbstractVcsLogTableModel newModel = myFilterer.applyFiltersAndUpdateUi(dataPack, getFilters());
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            myDataPack = dataPack;
            setModel(newModel, myDataPack, previouslySelected);
            myMainFrame.updateDataPack(myDataPack);
            setLongEdgeVisibility(myUiProperties.areLongEdgesVisible());
            fireFilterChangeEvent();
            repaintUI();

            if (newModel.getRowCount() == 0) { // getValueAt won't be called for empty model => need to explicitly request to load more
              newModel.requestToLoadMore(EmptyRunnable.INSTANCE);
            }
          }
        });
      }
    });
  }

  public void applyFiltersAndUpdateUi() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    applyFiltersAndUpdateUi(myDataPack);
  }

  @NotNull
  public VcsLogFilterCollection getFilters() {
    return myMainFrame.getFilterUi().getFilters();
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
    repaintUI();
  }

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
  public DataPack getDataPack() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myDataPack;
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
  public void addFilterChangeListener(@NotNull VcsLogFilterChangeListener listener) {
    myFilterChangeListeners.add(listener);
  }

  @Override
  public void removeFilterChangeListener(@NotNull VcsLogFilterChangeListener listener) {
    myFilterChangeListeners.remove(listener);
  }

  private void fireFilterChangeEvent() {
    for (VcsLogFilterChangeListener listener : myFilterChangeListeners) {
      listener.filtersPossiblyChanged();
    }
  }

  @Override
  public void dispose() {
    getTable().removeAllHighlighters();
  }

}
