package com.intellij.vcs.log.ui.frame;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.RefreshAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.ArrayUtil;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogFilterUi;
import com.intellij.vcs.log.VcsLogSettings;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.graph.impl.facade.bek.BekSorter;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.filter.VcsLogClassicFilterUi;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import icons.VcsLogIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class MainFrame extends JPanel implements TypeSafeDataProvider {

  @NotNull private final VcsLogDataHolder myLogDataHolder;
  @NotNull private final VcsLogUiImpl myUI;
  @NotNull private final Project myProject;
  @NotNull private final VcsLogUiProperties myUiProperties;
  @NotNull private final VcsLog myLog;
  @NotNull private final VcsLogClassicFilterUi myFilterUi;

  @NotNull private final JBLoadingPanel myChangesLoadingPane;
  @NotNull private final VcsLogGraphTable myGraphTable;
  @NotNull private final BranchesPanel myBranchesPanel;
  @NotNull private final DetailsPanel myDetailsPanel;
  @NotNull private final Splitter myDetailsSplitter;
  private final JComponent myToolbar;

  public MainFrame(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUiImpl vcsLogUI, @NotNull Project project,
                   @NotNull VcsLogSettings settings, @NotNull VcsLogUiProperties uiProperties, @NotNull VcsLog log,
                   @NotNull DataPack initialDataPack) {
    // collect info
    myLogDataHolder = logDataHolder;
    myUI = vcsLogUI;
    myProject = project;
    myUiProperties = uiProperties;
    myLog = log;
    myFilterUi = new VcsLogClassicFilterUi(myUI, logDataHolder, uiProperties, initialDataPack);

    // initialize components
    myGraphTable = new VcsLogGraphTable(vcsLogUI, logDataHolder, initialDataPack);
    myBranchesPanel = new BranchesPanel(logDataHolder, vcsLogUI, initialDataPack.getRefsModel());
    myBranchesPanel.setVisible(settings.isShowBranchesPanel());
    myDetailsPanel = new DetailsPanel(logDataHolder, myGraphTable, vcsLogUI.getColorManager(), initialDataPack);

    final RepositoryChangesBrowser changesBrowser = new RepositoryChangesBrowser(project, null, Collections.<Change>emptyList(), null);
    changesBrowser.getDiffAction().registerCustomShortcutSet(CommonShortcuts.getDiff(), getGraphTable());
    changesBrowser.getEditSourceAction().registerCustomShortcutSet(CommonShortcuts.getEditSource(), getGraphTable());
    setDefaultEmptyText(changesBrowser);
    myChangesLoadingPane = new JBLoadingPanel(new BorderLayout(), project);
    myChangesLoadingPane.add(changesBrowser);

    final CommitSelectionListener selectionChangeListener = new CommitSelectionListener(changesBrowser);
    myGraphTable.getSelectionModel().addListSelectionListener(selectionChangeListener);
    myGraphTable.getSelectionModel().addListSelectionListener(myDetailsPanel);
    updateWhenDetailsAreLoaded(selectionChangeListener);

    // layout
    myToolbar = createActionsToolbar();

    myDetailsSplitter = new Splitter(true, 0.7f);
    myDetailsSplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myGraphTable));
    setupDetailsSplitter(myUiProperties.isShowDetails());

    JComponent toolbars = new JPanel(new BorderLayout());
    toolbars.add(myToolbar, BorderLayout.NORTH);
    toolbars.add(myBranchesPanel, BorderLayout.CENTER);
    JComponent toolbarsAndTable = new JPanel(new BorderLayout());
    toolbarsAndTable.add(toolbars, BorderLayout.NORTH);
    toolbarsAndTable.add(myDetailsSplitter, BorderLayout.CENTER);

    final Splitter changesBrowserSplitter = new Splitter(false, 0.7f);
    changesBrowserSplitter.setFirstComponent(toolbarsAndTable);
    changesBrowserSplitter.setSecondComponent(myChangesLoadingPane);

    setLayout(new BorderLayout());
    add(changesBrowserSplitter);

    Disposer.register(logDataHolder, new Disposable() {
      public void dispose() {
        myDetailsSplitter.dispose();
        changesBrowserSplitter.dispose();
      }
    });
  }

  /**
   * Informs components that the actual DataPack has been updated (e.g. due to a log refresh). <br/>
   * Components may want to update their fields and/or rebuild.
   * @param dataPack new data pack.
   */
  public void updateDataPack(@NotNull DataPack dataPack) {
    myFilterUi.updateDataPack(dataPack);
    myDetailsPanel.updateDataPack(dataPack);
    myGraphTable.updateDataPack(dataPack);
  }

  private void updateWhenDetailsAreLoaded(final CommitSelectionListener selectionChangeListener) {
    myLogDataHolder.getMiniDetailsGetter().addDetailsLoadedListener(new Runnable() {
      @Override
      public void run() {
        myGraphTable.repaint();
      }
    });
    myLogDataHolder.getCommitDetailsGetter().addDetailsLoadedListener(new Runnable() {
      @Override
      public void run() {
        selectionChangeListener.valueChanged(null);
        myDetailsPanel.valueChanged(null);
      }
    });
    myLogDataHolder.getContainingBranchesGetter().setTaskCompletedListener(new Runnable() {
      @Override
      public void run() {
        myDetailsPanel.valueChanged(null);
      }
    });
  }

  public void setupDetailsSplitter(boolean state) {
    myDetailsSplitter.setSecondComponent(state ? myDetailsPanel : null);
  }

  private static void setDefaultEmptyText(ChangesBrowser changesBrowser) {
    changesBrowser.getViewer().setEmptyText("");
  }

  @NotNull
  public VcsLogGraphTable getGraphTable() {
    return myGraphTable;
  }

  @NotNull
  public VcsLogFilterUi getFilterUi() {
    return myFilterUi;
  }

  private JComponent createActionsToolbar() {
    AnAction bekAction = new BekAction();

    AnAction hideBranchesAction = new GraphAction("Collapse linear branches", "Collapse linear branches", VcsLogIcons.CollapseBranches) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myUI.hideAll();
      }
    };

    AnAction showBranchesAction = new GraphAction("Expand all branches", "Expand all branches", VcsLogIcons.ExpandBranches) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myUI.showAll();
      }
    };

    RefreshAction refreshAction = new RefreshAction("Refresh", "Refresh", AllIcons.Actions.Refresh) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myLogDataHolder.refreshCompletely();
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(true);
      }
    };

    AnAction showFullPatchAction = new ShowLongEdgesAction();
    AnAction showDetailsAction = new ShowDetailsAction();

    refreshAction.registerShortcutOn(this);

    DefaultActionGroup toolbarGroup = new DefaultActionGroup(bekAction, hideBranchesAction, showBranchesAction, showFullPatchAction, refreshAction,
                                                             showDetailsAction);
    toolbarGroup.add(ActionManager.getInstance().getAction(VcsLogUiImpl.TOOLBAR_ACTION_GROUP));

    DefaultActionGroup mainGroup = new DefaultActionGroup();
    mainGroup.add(myFilterUi.getFilterActionComponents());
    mainGroup.addSeparator();
    mainGroup.add(toolbarGroup);
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, mainGroup, true);
    toolbar.setTargetComponent(this);
    return toolbar.getComponent();
  }

  public JComponent getMainComponent() {
    return this;
  }

  public void setBranchesPanelVisible(boolean visible) {
    myBranchesPanel.setVisible(visible);
  }

  @Nullable
  public List<Change> getSelectedChanges() {
    return myGraphTable.getSelectedChanges();
  }

  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (VcsLogDataKeys.VSC_LOG == key) {
      sink.put(key, myLog);
    }
    else if (VcsLogDataKeys.VCS_LOG_UI == key) {
      sink.put(key, myUI);
    }
    else if (VcsLogDataKeys.VCS_LOG_DATA_PROVIDER == key) {
      sink.put(key, myLogDataHolder);
    }
    else if (VcsDataKeys.CHANGES == key || VcsDataKeys.SELECTED_CHANGES == key) {
      List<Change> selectedChanges = getSelectedChanges();
      if (selectedChanges != null) {
        sink.put(key, ArrayUtil.toObjectArray(selectedChanges, Change.class));
      }
    }
  }

  public Component getToolbar() {
    return myToolbar;
  }

  public boolean areGraphActionsEnabled() {
    return myGraphTable.getModel() instanceof GraphTableModel && myGraphTable.getRowCount() > 0;
  }

  private class CommitSelectionListener implements ListSelectionListener {
    private final ChangesBrowser myChangesBrowser;

    public CommitSelectionListener(ChangesBrowser changesBrowser) {
      myChangesBrowser = changesBrowser;
    }

    @Override
    public void valueChanged(@Nullable ListSelectionEvent notUsed) {
      int rows = getGraphTable().getSelectedRowCount();
      if (rows < 1) {
        myChangesLoadingPane.stopLoading();
        setDefaultEmptyText(myChangesBrowser);
        myChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
      }
      else {
        List<Change> selectedChanges = getSelectedChanges();
        if (selectedChanges != null) {
          myChangesLoadingPane.stopLoading();
          myChangesBrowser.setChangesToDisplay(selectedChanges);
        }
        else {
          myChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
          myChangesLoadingPane.startLoading();
        }
      }
    }
  }

  private class BekAction extends ToggleAction implements DumbAware {
    public BekAction() {
      super("BEK", "BEK", AllIcons.Actions.Lightning);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myUI.isBek();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myUI.setBek(state);
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(BekSorter.isBekEnabled());
      e.getPresentation().setEnabled(areGraphActionsEnabled());
    }
  }

  private class ShowDetailsAction extends ToggleAction implements DumbAware {

    public ShowDetailsAction() {
      super("Show Details", "Display details panel", AllIcons.Actions.Preview);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return !myProject.isDisposed() && myUiProperties.isShowDetails();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      setupDetailsSplitter(state);
      if (!myProject.isDisposed()) {
        myUiProperties.setShowDetails(state);
      }
    }
  }

  private class ShowLongEdgesAction extends ToggleAction implements DumbAware {
    public ShowLongEdgesAction() {
      super("Show long edges", "Show long branch edges even if commits are invisible in the current view.", VcsLogIcons.ShowHideLongEdges);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return !myUI.areLongEdgesHidden();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myUI.setLongEdgeVisibility(state);
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(areGraphActionsEnabled());
    }
  }

  private abstract class GraphAction extends DumbAwareAction {

    public GraphAction(String text, String description, Icon icon) {
      super(text, description, icon);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(areGraphActionsEnabled());
    }
  }
}
