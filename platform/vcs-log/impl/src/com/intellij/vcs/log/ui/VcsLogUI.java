package com.intellij.vcs.log.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogSettings;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogFilterer;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.graph.ClickGraphAction;
import com.intellij.vcs.log.graph.LinearBranchesExpansionAction;
import com.intellij.vcs.log.graph.LongEdgesAction;
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
  @NotNull private final VcsLogUiProperties myUiProperties;
  @NotNull private final VcsLogFilterer myFilterer;
  @NotNull private final VcsLog myLog;

  public VcsLogUI(@NotNull VcsLogDataHolder logDataHolder, @NotNull Project project, @NotNull VcsLogSettings settings,
                  @NotNull VcsLogColorManager manager, @NotNull VcsLogUiProperties uiProperties) {
    myLogDataHolder = logDataHolder;
    myProject = project;
    myColorManager = manager;
    myUiProperties = uiProperties;
    myFilterer = new VcsLogFilterer(logDataHolder, this);
    myLog = new VcsLogImpl(myLogDataHolder, this);
    myMainFrame = new MainFrame(myLogDataHolder, this, project, settings, uiProperties, myLog);
    project.getMessageBus().connect(project).subscribe(VcsLogDataHolder.REFRESH_COMPLETED, new Runnable() {
      @Override
      public void run() {
        applyFiltersAndUpdateUi();
      }
    });
    applyFiltersAndUpdateUi();
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
        myLogDataHolder.getDataPack().getGraphFacade().performAction(LinearBranchesExpansionAction.EXPAND);
        updateUI();
        jumpToRow(0);
      }
    });
  }

  public void hideAll() {
    runUnderModalProgress("Collapsing linear branches...", new Runnable() {
      @Override
      public void run() {
        myLogDataHolder.getDataPack().getGraphFacade().performAction(LinearBranchesExpansionAction.COLLAPSE);
        updateUI();
        jumpToRow(0);
      }
    });
  }

  public void setLongEdgeVisibility(boolean visibility) {
    myLogDataHolder.getDataPack().getGraphFacade().performAction(LongEdgesAction.valueOf(visibility));
    updateUI();
  }

  public boolean areLongEdgesHidden() {
    return myLogDataHolder.getDataPack().getGraphFacade().getInfoProvider().areLongEdgesHidden();
  }

  public void click(int rowIndex) {
    DataPack dataPack = myLogDataHolder.getDataPack();
    dataPack.getGraphFacade().performAction(new ClickGraphAction(rowIndex, null));
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

  public void applyFiltersAndUpdateUi() {
    runUnderModalProgress("Applying filters...", new Runnable() {
      public void run() {
        myFilterer.applyFiltersAndUpdateUi(collectFilters());
      }
    });
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
}
