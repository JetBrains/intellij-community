package com.intellij.vcs.log.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.VcsLogSettings;
import com.intellij.vcs.log.compressedlist.UpdateRequest;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogFilterer;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.graph.elements.GraphElement;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graphmodel.FragmentManager;
import com.intellij.vcs.log.graphmodel.GraphFragment;
import com.intellij.vcs.log.printmodel.SelectController;
import com.intellij.vcs.log.ui.frame.MainFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableModel;
import java.util.Collection;

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

  @Nullable private GraphElement prevGraphElement;

  public VcsLogUI(@NotNull VcsLogDataHolder logDataHolder, @NotNull Project project, @NotNull VcsLogSettings settings,
                  @NotNull VcsLogColorManager manager, @NotNull VcsLogUiProperties uiProperties) {
    myLogDataHolder = logDataHolder;
    myProject = project;
    myColorManager = manager;
    myUiProperties = uiProperties;
    myFilterer = new VcsLogFilterer(logDataHolder, this);
    myMainFrame = new MainFrame(myLogDataHolder, this, project, uiProperties);
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
        click(rowIndex);
      }
    });
  }

  public void setModel(@NotNull TableModel model) {
    myMainFrame.getGraphTable().setModel(model);
  }

  public void updateUI() {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myMainFrame.getGraphTable().setPreferredColumnWidths();
        myMainFrame.getGraphTable().repaint();
        myMainFrame.refresh();
      }
    });
  }

  public void addToSelection(final Hash hash) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        int row = myLogDataHolder.getDataPack().getRowByHash(hash);
        myMainFrame.getGraphTable().getSelectionModel().addSelectionInterval(row, row);
      }
    });
  }

  public void showAll() {
    myLogDataHolder.getDataPack().getGraphModel().getFragmentManager().showAll();
    updateUI();
    jumpToRow(0);
  }

  public void hideAll() {
    myLogDataHolder.getDataPack().getGraphModel().getFragmentManager().hideAll();
    updateUI();
    jumpToRow(0);
  }

  public void setLongEdgeVisibility(boolean visibility) {
    myLogDataHolder.getDataPack().getPrintCellModel().setLongEdgeVisibility(visibility);
    updateUI();
  }

  public boolean areLongEdgesHidden() {
    return myLogDataHolder.getDataPack().getPrintCellModel().areLongEdgesHidden();
  }

  public void over(@Nullable GraphElement graphElement) {
    SelectController selectController = myLogDataHolder.getDataPack().getPrintCellModel().getSelectController();
    FragmentManager fragmentManager = myLogDataHolder.getDataPack().getGraphModel().getFragmentManager();
    if (graphElement == prevGraphElement) {
      return;
    }
    else {
      prevGraphElement = graphElement;
    }
    selectController.deselectAll();
    if (graphElement == null) {
      updateUI();
    }
    else {
      GraphFragment graphFragment = fragmentManager.relateFragment(graphElement);
      selectController.select(graphFragment);
      updateUI();
    }
  }

  public void click(@Nullable GraphElement graphElement) {
    SelectController selectController = myLogDataHolder.getDataPack().getPrintCellModel().getSelectController();
    FragmentManager fragmentController = myLogDataHolder.getDataPack().getGraphModel().getFragmentManager();
    selectController.deselectAll();
    if (graphElement == null) {
      return;
    }
    GraphFragment fragment = fragmentController.relateFragment(graphElement);
    if (fragment == null) {
      return;
    }
    UpdateRequest updateRequest = fragmentController.changeVisibility(fragment);
    updateUI();
    jumpToRow(updateRequest.from());
  }

  public void click(int rowIndex) {
    DataPack dataPack = myLogDataHolder.getDataPack();
    dataPack.getPrintCellModel().getCommitSelectController().deselectAll();
    Node node = dataPack.getNode(rowIndex);
    if (node != null) {
      FragmentManager fragmentController = dataPack.getGraphModel().getFragmentManager();
      dataPack.getPrintCellModel().getCommitSelectController().select(fragmentController.allCommitsCurrentBranch(node));
    }
    updateUI();
  }

  public void jumpToCommit(final Hash commitHash) {
    int row = myLogDataHolder.getDataPack().getRowByHash(commitHash);
    if (row != -1) {
      jumpToRow(row);
    }
    else {
      myLogDataHolder.showFullLog(new Runnable() {
        @Override
        public void run() {
          jumpToCommit(commitHash);
        }
      });
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
  public VcsLogDataHolder getLogDataHolder() {
    return myLogDataHolder;
  }

  public void applyFiltersAndUpdateUi() {
    myFilterer.applyFiltersAndUpdateUi(collectFilters());
  }

  @NotNull
  public Collection<VcsLogFilter> collectFilters() {
    return myMainFrame.getFilterUi().getFilters();
  }

  public JBTable getTable() {
    return myMainFrame.getGraphTable();
  }

  @NotNull
  public VcsLogUiProperties getUiProperties() {
    return myUiProperties;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }
}
