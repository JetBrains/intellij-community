package com.intellij.vcs.log.ui.frame;

import com.google.common.primitives.Ints;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.TextRevisionNumber;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import com.intellij.vcs.CommittedChangeListForRevision;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogProgress;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.actions.IntelliSortChooserPopupAction;
import com.intellij.vcs.log.ui.filter.VcsLogClassicFilterUi;
import com.intellij.vcs.log.ui.table.CommitSelectionListener;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.BekUtil;
import com.intellij.vcs.log.util.VcsUserUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class MainFrame extends JPanel implements DataProvider, Disposable {
  private static final String HELP_ID = "reference.changesToolWindow.log";

  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogUiImpl myUi;
  @NotNull private final VcsLog myLog;
  @NotNull private final VcsLogClassicFilterUi myFilterUi;

  @NotNull private final JBLoadingPanel myChangesLoadingPane;
  @NotNull private final VcsLogGraphTable myGraphTable;
  @NotNull private final DetailsPanel myDetailsPanel;
  @NotNull private final Splitter myDetailsSplitter;
  @NotNull private final JComponent myToolbar;
  @NotNull private final RepositoryChangesBrowser myChangesBrowser;
  @NotNull private final Splitter myChangesBrowserSplitter;
  @NotNull private final SearchTextField myTextFilter;
  @NotNull private final MainVcsLogUiProperties myUiProperties;

  @NotNull private Runnable myContainingBranchesListener;
  @NotNull private Runnable myMiniDetailsLoadedListener;

  public MainFrame(@NotNull VcsLogData logData,
                   @NotNull VcsLogUiImpl ui,
                   @NotNull Project project,
                   @NotNull MainVcsLogUiProperties uiProperties,
                   @NotNull VcsLog log,
                   @NotNull VisiblePack initialDataPack) {
    // collect info
    myLogData = logData;
    myUi = ui;
    myLog = log;
    myUiProperties = uiProperties;

    myFilterUi = new VcsLogClassicFilterUi(myUi, logData, myUiProperties, initialDataPack);

    // initialize components
    myGraphTable = new VcsLogGraphTable(ui, logData, initialDataPack);
    myDetailsPanel = new DetailsPanel(logData, ui.getColorManager(), this);

    myChangesBrowser = new RepositoryChangesBrowser(project, null, Collections.emptyList(), null) {
      @Override
      protected void buildToolBar(DefaultActionGroup toolBarGroup) {
        super.buildToolBar(toolBarGroup);
        toolBarGroup.add(ActionManager.getInstance().getAction(VcsLogActionPlaces.VCS_LOG_SHOW_DETAILS_ACTION));
      }
    };
    myChangesBrowser.getViewerScrollPane().setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    myChangesBrowser.getDiffAction().registerCustomShortcutSet(myChangesBrowser.getDiffAction().getShortcutSet(), getGraphTable());
    myChangesBrowser.getEditSourceAction().registerCustomShortcutSet(CommonShortcuts.getEditSource(), getGraphTable());
    myChangesBrowser.getViewer().setEmptyText("");
    myChangesLoadingPane = new JBLoadingPanel(new BorderLayout(), this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS);
    myChangesLoadingPane.add(myChangesBrowser);

    myDetailsSplitter = new OnePixelSplitter(true, "vcs.log.details.splitter.proportion", 0.7f);
    myDetailsSplitter.setFirstComponent(myChangesLoadingPane);
    setupDetailsSplitter(myUiProperties.get(MainVcsLogUiProperties.SHOW_DETAILS));

    myGraphTable.getSelectionModel().addListSelectionListener(new CommitSelectionListenerForDiff());
    myDetailsPanel.installCommitSelectionListener(myGraphTable);
    updateWhenDetailsAreLoaded();

    myTextFilter = myFilterUi.createTextFilter();
    myToolbar = createActionsToolbar();

    ProgressStripe progressStripe =
      new ProgressStripe(setupScrolledGraph(), this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
        @Override
        public void updateUI() {
          super.updateUI();
          if (myDecorator != null && myLogData.getProgress().isRunning()) startLoadingImmediately();
        }
      };
    myLogData.getProgress().addProgressIndicatorListener(new VcsLogProgress.ProgressListener() {
      @Override
      public void progressStarted() {
        progressStripe.startLoading();
      }

      @Override
      public void progressStopped() {
        progressStripe.stopLoading();
      }
    }, this);


    JComponent toolbars = new JPanel(new BorderLayout());
    toolbars.add(myToolbar, BorderLayout.NORTH);
    JComponent toolbarsAndTable = new JPanel(new BorderLayout());
    toolbarsAndTable.add(toolbars, BorderLayout.NORTH);
    toolbarsAndTable.add(progressStripe, BorderLayout.CENTER);

    myChangesBrowserSplitter = new OnePixelSplitter(false, "vcs.log.changes.splitter.proportion", 0.7f);
    myChangesBrowserSplitter.setFirstComponent(toolbarsAndTable);
    myChangesBrowserSplitter.setSecondComponent(myDetailsSplitter);

    setLayout(new BorderLayout());
    add(myChangesBrowserSplitter);

    Disposer.register(ui, this);
    myGraphTable.resetDefaultFocusTraversalKeys();
    setFocusCycleRoot(true);
    setFocusTraversalPolicy(new MyFocusPolicy());
  }

  /**
   * Informs components that the actual DataPack has been updated (e.g. due to a log refresh). <br/>
   * Components may want to update their fields and/or rebuild.
   *
   * @param dataPack         new data pack.
   * @param permGraphChanged true if permanent graph itself was changed.
   */
  public void updateDataPack(@NotNull VisiblePack dataPack, boolean permGraphChanged) {
    myFilterUi.updateDataPack(dataPack);
    myGraphTable.updateDataPack(dataPack, permGraphChanged);
  }

  private void updateWhenDetailsAreLoaded() {
    myMiniDetailsLoadedListener = () -> {
      myGraphTable.initColumnSize();
      myGraphTable.repaint();
    };
    myContainingBranchesListener = () -> {
      myDetailsPanel.branchesChanged();
      myGraphTable.repaint(); // we may need to repaint highlighters
    };
    myLogData.getMiniDetailsGetter().addDetailsLoadedListener(myMiniDetailsLoadedListener);
    myLogData.getContainingBranchesGetter().addTaskCompletedListener(myContainingBranchesListener);
  }

  public void setupDetailsSplitter(boolean state) {
    myDetailsSplitter.setSecondComponent(state ? myDetailsPanel : null);
  }

  @NotNull
  private JScrollPane setupScrolledGraph() {
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myGraphTable, SideBorder.TOP);
    myGraphTable.viewportSet(scrollPane.getViewport());
    return scrollPane;
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
    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(ActionManager.getInstance().getAction(VcsLogActionPlaces.TOOLBAR_ACTION_GROUP));

    DefaultActionGroup mainGroup = new DefaultActionGroup();
    mainGroup.add(ActionManager.getInstance().getAction(VcsLogActionPlaces.VCS_LOG_TEXT_FILTER_SETTINGS_ACTION));
    mainGroup.add(new Separator());
    mainGroup.add(myFilterUi.createActionGroup());
    mainGroup.addSeparator();
    if (BekUtil.isBekEnabled()) {
      if (BekUtil.isLinearBekEnabled()) {
        mainGroup.add(new IntelliSortChooserPopupAction());
        // can not register both of the actions in xml file, choosing to register an action for the "outer world"
        // I can of course if linear bek is enabled replace the action on start but why bother
      }
      else {
        mainGroup.add(ActionManager.getInstance().getAction(VcsLogActionPlaces.VCS_LOG_INTELLI_SORT_ACTION));
      }
    }
    mainGroup.add(toolbarGroup);
    ActionToolbar toolbar = createActionsToolbar(mainGroup);

    Wrapper textFilter = new Wrapper(myTextFilter);
    textFilter.setVerticalSizeReferent(toolbar.getComponent());
    textFilter.setBorder(JBUI.Borders.emptyLeft(5));

    ActionToolbar settings =
      createActionsToolbar(new DefaultActionGroup(ActionManager.getInstance().getAction(VcsLogActionPlaces.VCS_LOG_QUICK_SETTINGS_ACTION)));
    settings.setReservePlaceAutoPopupIcon(false);
    settings.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);

    JPanel panel = new JPanel(new MigLayout("ins 0, fill", "[left]0[left, fill]push[right]", "center"));
    panel.add(textFilter);
    panel.add(toolbar.getComponent());
    panel.add(settings.getComponent());
    return panel;
  }

  @NotNull
  private ActionToolbar createActionsToolbar(@NotNull DefaultActionGroup mainGroup) {
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CHANGES_VIEW_TOOLBAR, mainGroup, true);
    toolbar.setTargetComponent(this);
    return toolbar;
  }

  @NotNull
  public JComponent getMainComponent() {
    return this;
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (VcsLogDataKeys.VCS_LOG.is(dataId)) {
      return myLog;
    }
    else if (VcsLogDataKeys.VCS_LOG_UI.is(dataId)) {
      return myUi;
    }
    else if (VcsLogDataKeys.VCS_LOG_DATA_PROVIDER.is(dataId)) {
      return myLogData;
    }
    else if (VcsDataKeys.CHANGES.is(dataId) || VcsDataKeys.SELECTED_CHANGES.is(dataId)) {
      return ArrayUtil.toObjectArray(myChangesBrowser.getCurrentDisplayedChanges(), Change.class);
    }
    else if (VcsDataKeys.CHANGE_LISTS.is(dataId)) {
      List<VcsFullCommitDetails> details = myLog.getSelectedDetails();
      if (details.size() > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
      return ContainerUtil.map2Array(details, CommittedChangeListForRevision.class,
                                     detail -> new CommittedChangeListForRevision(detail.getSubject(), detail.getFullMessage(),
                                                                                  VcsUserUtil.getShortPresentation(detail.getCommitter()),
                                                                                  new Date(detail.getCommitTime()),
                                                                                  detail.getChanges(),
                                                                                  convertToRevisionNumber(detail.getId())));
    }
    else if (VcsDataKeys.VCS_REVISION_NUMBERS.is(dataId)) {
      List<CommitId> hashes = myLog.getSelectedCommits();
      if (hashes.size() > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
      return ArrayUtil
        .toObjectArray(ContainerUtil.map(hashes, commitId -> convertToRevisionNumber(commitId.getHash())), VcsRevisionNumber.class);
    }
    else if (VcsDataKeys.VCS.is(dataId)) {
      int[] selectedRows = myGraphTable.getSelectedRows();
      if (selectedRows.length == 0 || selectedRows.length > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
      Set<VirtualFile> roots = ContainerUtil.map2Set(Ints.asList(selectedRows), row -> myGraphTable.getModel().getRoot(row));
      if (roots.size() == 1) {
        return myLogData.getLogProvider(assertNotNull(getFirstItem(roots))).getSupportedVcs();
      }
    }
    else if (VcsLogDataKeys.VCS_LOG_BRANCHES.is(dataId)) {
      int[] selectedRows = myGraphTable.getSelectedRows();
      if (selectedRows.length != 1) return null;
      return myGraphTable.getModel().getBranchesAtRow(selectedRows[0]);
    }
    else if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return HELP_ID;
    }
    else if (VcsLogInternalDataKeys.LOG_UI_PROPERTIES.is(dataId)) {
      return myUiProperties;
    }
    return null;
  }

  @NotNull
  public JComponent getToolbar() {
    return myToolbar;
  }

  @NotNull
  public SearchTextField getTextFilter() {
    return myTextFilter;
  }

  public boolean areGraphActionsEnabled() {
    return myGraphTable.getRowCount() > 0;
  }

  @NotNull
  private static TextRevisionNumber convertToRevisionNumber(@NotNull Hash hash) {
    return new TextRevisionNumber(hash.asString(), hash.toShortString());
  }

  public void showDetails(boolean state) {
    myDetailsSplitter.setSecondComponent(state ? myDetailsPanel : null);
  }

  @Override
  public void dispose() {
    myLogData.getMiniDetailsGetter().removeDetailsLoadedListener(myMiniDetailsLoadedListener);
    myLogData.getContainingBranchesGetter().removeTaskCompletedListener(myContainingBranchesListener);

    myDetailsSplitter.dispose();
    myChangesBrowserSplitter.dispose();
  }

  private class CommitSelectionListenerForDiff extends CommitSelectionListener {
    protected CommitSelectionListenerForDiff() {
      super(myLogData, MainFrame.this.myGraphTable, myChangesLoadingPane);
    }

    @Override
    protected void onDetailsLoaded(@NotNull List<VcsFullCommitDetails> detailsList) {
      List<Change> changes = ContainerUtil.newArrayList();
      List<VcsFullCommitDetails> detailsListReversed = ContainerUtil.reverse(detailsList);
      for (VcsFullCommitDetails details : detailsListReversed) {
        changes.addAll(details.getChanges());
      }
      changes = CommittedChangesTreeBrowser.zipChanges(changes);
      myChangesBrowser.setChangesToDisplay(changes);
    }

    @Override
    protected void onSelection(@NotNull int[] selection) {
      // just reset and wait for details to be loaded
      myChangesBrowser.setChangesToDisplay(Collections.emptyList());
      myChangesBrowser.getViewer().setEmptyText("");
    }

    @Override
    protected void onEmptySelection() {
      myChangesBrowser.getViewer().setEmptyText("No commits selected");
      myChangesBrowser.setChangesToDisplay(Collections.emptyList());
    }
  }

  private class MyFocusPolicy extends ComponentsListFocusTraversalPolicy {
    @NotNull
    @Override
    protected List<Component> getOrderedComponents() {
      return Arrays.asList(myGraphTable, myChangesBrowser.getPreferredFocusedComponent(), myTextFilter.getTextEditor());
    }
  }
}
