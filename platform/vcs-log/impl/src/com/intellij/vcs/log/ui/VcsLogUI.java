package com.intellij.vcs.log.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogSettings;
import com.intellij.vcs.log.data.*;
import com.intellij.vcs.log.graph.*;
import com.intellij.vcs.log.impl.VcsLogImpl;
import com.intellij.vcs.log.ui.frame.MainFrame;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import com.intellij.vcs.log.ui.tables.AbstractVcsLogTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableModel;
import java.awt.*;
import java.util.Set;

/**
 * @author erokhins
 */
public class VcsLogUI {

  public static final String POPUP_ACTION_GROUP = "Vcs.Log.ContextMenu";
  public static final String TOOLBAR_ACTION_GROUP = "Vcs.Log.Toolbar";
  public static final String VCS_LOG_TABLE_PLACE = "Vcs.Log.ContextMenu";

  private static final Logger LOG = Logger.getInstance(VcsLogUI.class);

  @NotNull private final VcsLogDataHolder myLogDataHolder;
  @NotNull private final MainFrame myMainFrame;
  @NotNull private final Project myProject;
  @NotNull private final VcsLogColorManager myColorManager;
  @NotNull private final VcsLogFilterer myFilterer;
  @NotNull private final VcsLog myLog;

  @NotNull private DataPack myDataPack;

  public VcsLogUI(@NotNull VcsLogDataHolder logDataHolder, @NotNull Project project, @NotNull VcsLogSettings settings,
                  @NotNull VcsLogColorManager manager, @NotNull VcsLogUiProperties uiProperties, @NotNull DataPack initialDataPack) {
    myLogDataHolder = logDataHolder;
    myProject = project;
    myColorManager = manager;
    myDataPack = initialDataPack;
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

  public void setModel(@NotNull AbstractVcsLogTableModel model) {
    VcsLogGraphTable table = getTable();
    int[] selectedRows = table.getSelectedRows();
    TableModel previousModel = table.getModel();

    table.setModel(model);

    if (previousModel instanceof AbstractVcsLogTableModel) { // initially it is an empty DefaultTableModel
      restoreSelection(table, (AbstractVcsLogTableModel)previousModel, selectedRows, model);
    }
    table.setPaintBusy(false);
  }

  private static void restoreSelection(@NotNull VcsLogGraphTable table, @NotNull AbstractVcsLogTableModel previousModel,
                                       int[] previousSelectedRows, @NotNull AbstractVcsLogTableModel newModel) {
    Set<Hash> selectedHashes = getHashesAtRows(previousModel, previousSelectedRows);
    Set<Integer> rowsToSelect = findNewRowsToSelect(newModel, selectedHashes);
    for (Integer row : rowsToSelect) {
      table.addRowSelectionInterval(row, row);
    }
  }

  @NotNull
  private static Set<Hash> getHashesAtRows(@NotNull AbstractVcsLogTableModel model, int[] rows) {
    Set<Hash> hashes = ContainerUtil.newHashSet();
    for (int row : rows) {
      Hash hash = model.getHashAtRow(row);
      if (hash != null) {
        hashes.add(hash);
      }
    }
    return hashes;
  }

  @NotNull
  private static Set<Integer> findNewRowsToSelect(@NotNull AbstractVcsLogTableModel model, @NotNull Set<Hash> selectedHashes) {
    Set<Integer> rowsToSelect = ContainerUtil.newHashSet();
    for (int row = 0; row < model.getRowCount() && rowsToSelect.size() < selectedHashes.size(); row++) {//stop iterating if found all hashes
      Hash hash = model.getHashAtRow(row);
      if (hash != null && selectedHashes.contains(hash)) {
        rowsToSelect.add(row);
      }
    }
    return rowsToSelect;
  }

  public void updateUI() {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myMainFrame.getGraphTable().repaint();
      }
    });
  }

  public void showAll() {
    runUnderModalProgress("Expanding linear branches...", new Runnable() {
      @Override
      public void run() {
        myDataPack.getGraphFacade().performAction(LinearBranchesExpansionAction.EXPAND);
        updateUI();
        jumpToRow(0);
      }
    });
  }

  public void hideAll() {
    runUnderModalProgress("Collapsing linear branches...", new Runnable() {
      @Override
      public void run() {
        myDataPack.getGraphFacade().performAction(LinearBranchesExpansionAction.COLLAPSE);
        updateUI();
        jumpToRow(0);
      }
    });
  }

  public void setLongEdgeVisibility(boolean visibility) {
    myDataPack.getGraphFacade().performAction(LongEdgesAction.valueOf(visibility));
    updateUI();
  }

  public boolean areLongEdgesHidden() {
    return myDataPack.getGraphFacade().getInfoProvider().areLongEdgesHidden();
  }

  public void click(int rowIndex) {
    myDataPack.getGraphFacade().performAction(new ClickGraphAction(rowIndex, null));
    updateUI();
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
    myMainFrame.setCursor(Cursor.getDefaultCursor());
    updateUI();

    if (answer == null) {
      return;
    }
    GraphActionRequest actionRequest = answer.getActionRequest();
    if (actionRequest instanceof JumpToRowActionRequest) {
      int row = ((JumpToRowActionRequest)actionRequest).getRow();
      jumpToRow(row);
    }
    else if (actionRequest instanceof ChangeCursorActionRequest) {
      myMainFrame.setCursor(((ChangeCursorActionRequest)actionRequest).getCursor());
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
    if (collectFilters().isEmpty()) {
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

  private void applyFiltersAndUpdateUi(@NotNull final DataPack dataPack) {
    runUnderModalProgress("Applying filters...", new Runnable() {
      public void run() {
        final AbstractVcsLogTableModel newModel = myFilterer.applyFiltersAndUpdateUi(dataPack, collectFilters());
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            myDataPack = dataPack;
            setModel(newModel);
            myMainFrame.updateDataPack(myDataPack);
            updateUI();

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
  public VcsLogFilterCollection collectFilters() {
    return myMainFrame.getFilterUi().getFilters();
  }

  public VcsLogGraphTable getTable() {
    return myMainFrame.getGraphTable();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public void runUnderModalProgress(@NotNull String task, @NotNull Runnable runnable) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, task, false, null, this.getMainFrame().getMainComponent());
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
  public DataPack getDataPack() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myDataPack;
  }
}
