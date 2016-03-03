package com.intellij.vcs.log.ui.frame;

import com.google.common.primitives.Ints;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.TextRevisionNumber;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.committed.RepositoryChangesBrowser;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import com.intellij.vcs.CommittedChangeListForRevision;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogDataManager;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.actions.IntelliSortChooserPopupAction;
import com.intellij.vcs.log.ui.filter.VcsLogClassicFilterUi;
import com.intellij.vcs.log.util.BekUtil;
import com.intellij.vcs.log.util.VcsUserUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class MainFrame extends JPanel implements DataProvider, Disposable {

  @NotNull private final VcsLogDataManager myLogDataManager;
  @NotNull private final VcsLogUiImpl myUi;
  @NotNull private final VcsLog myLog;
  @NotNull private final VcsLogClassicFilterUi myFilterUi;

  @NotNull private final JBLoadingPanel myChangesLoadingPane;
  @NotNull private final VcsLogGraphTable myGraphTable;
  @NotNull private final BranchesPanel myBranchesPanel;
  @NotNull private final DetailsPanel myDetailsPanel;
  @NotNull private final Splitter myDetailsSplitter;
  @NotNull private final JComponent myToolbar;
  @NotNull private final RepositoryChangesBrowser myChangesBrowser;
  @NotNull private final Splitter myChangesBrowserSplitter;
  @NotNull private final SearchTextField myTextFilter;

  @NotNull private Runnable myTaskCompletedListener;
  @NotNull private Runnable myFullDetailsLoadedListener;
  @NotNull private Runnable myMiniDetailsLoadedListener;

  public MainFrame(@NotNull VcsLogDataManager logDataManager,
                   @NotNull VcsLogUiImpl ui,
                   @NotNull Project project,
                   @NotNull VcsLogUiProperties uiProperties,
                   @NotNull VcsLog log,
                   @NotNull VisiblePack initialDataPack) {
    // collect info
    myLogDataManager = logDataManager;
    myUi = ui;
    myLog = log;
    myFilterUi = new VcsLogClassicFilterUi(myUi, logDataManager, uiProperties, initialDataPack);

    // initialize components
    myGraphTable = new VcsLogGraphTable(ui, logDataManager, initialDataPack);
    myBranchesPanel = new BranchesPanel(logDataManager, ui, initialDataPack.getRefs());
    JComponent branchScrollPane = myBranchesPanel.createScrollPane();
    branchScrollPane.setVisible(uiProperties.isShowBranchesPanel());
    myDetailsPanel = new DetailsPanel(logDataManager, myGraphTable, ui.getColorManager(), initialDataPack);

    myChangesBrowser = new RepositoryChangesBrowser(project, null, Collections.<Change>emptyList(), null);
    myChangesBrowser.getViewer().setScrollPaneBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    myChangesBrowser.getDiffAction().registerCustomShortcutSet(myChangesBrowser.getDiffAction().getShortcutSet(), getGraphTable());
    myChangesBrowser.getEditSourceAction().registerCustomShortcutSet(CommonShortcuts.getEditSource(), getGraphTable());
    setDefaultEmptyText(myChangesBrowser);
    myChangesLoadingPane = new JBLoadingPanel(new BorderLayout(), project, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS);
    myChangesLoadingPane.add(myChangesBrowser);

    final CommitSelectionListener selectionChangeListener = new CommitSelectionListener(myChangesBrowser);
    myGraphTable.getSelectionModel().addListSelectionListener(selectionChangeListener);
    myGraphTable.getSelectionModel().addListSelectionListener(myDetailsPanel);
    updateWhenDetailsAreLoaded();

    myTextFilter = myFilterUi.createTextFilter();
    myToolbar = createActionsToolbar();

    myDetailsSplitter = new OnePixelSplitter(true, 0.7f);
    myDetailsSplitter.setFirstComponent(setupScrolledGraph());
    setupDetailsSplitter(uiProperties.isShowDetails());

    JComponent toolbars = new JPanel(new BorderLayout());
    toolbars.add(myToolbar, BorderLayout.NORTH);
    toolbars.add(branchScrollPane, BorderLayout.CENTER);
    JComponent toolbarsAndTable = new JPanel(new BorderLayout());
    toolbarsAndTable.add(toolbars, BorderLayout.NORTH);
    toolbarsAndTable.add(myDetailsSplitter, BorderLayout.CENTER);

    myChangesBrowserSplitter = new OnePixelSplitter(false, 0.7f);
    myChangesBrowserSplitter.setFirstComponent(toolbarsAndTable);
    myChangesBrowserSplitter.setSecondComponent(myChangesLoadingPane);

    setLayout(new BorderLayout());
    add(myChangesBrowserSplitter);

    Disposer.register(logDataManager, this);
    myGraphTable.resetDefaultFocusTraversalKeys();
    setFocusTraversalPolicyProvider(true);
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
    myDetailsPanel.updateDataPack(dataPack);
    myGraphTable.updateDataPack(dataPack, permGraphChanged);
    myBranchesPanel.updateDataPack(dataPack, permGraphChanged);
  }

  private void updateWhenDetailsAreLoaded() {
    myMiniDetailsLoadedListener = new Runnable() {
      @Override
      public void run() {
        myGraphTable.initColumnSize();
        myGraphTable.repaint();
      }
    };
    myFullDetailsLoadedListener = new Runnable() {
      @Override
      public void run() {
        myDetailsPanel.valueChanged(null);
      }
    };
    myTaskCompletedListener = new Runnable() {
      @Override
      public void run() {
        myDetailsPanel.valueChanged(null);
        myGraphTable.repaint(); // we may need to repaint highlighters
      }
    };
    myLogDataManager.getMiniDetailsGetter().addDetailsLoadedListener(myMiniDetailsLoadedListener);
    myLogDataManager.getCommitDetailsGetter().addDetailsLoadedListener(myFullDetailsLoadedListener);
    myLogDataManager.getContainingBranchesGetter().addTaskCompletedListener(myTaskCompletedListener);
  }

  public void setupDetailsSplitter(boolean state) {
    myDetailsSplitter.setSecondComponent(state ? myDetailsPanel : null);
  }

  private JScrollPane setupScrolledGraph() {
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myGraphTable, SideBorder.TOP);
    myGraphTable.viewportSet(scrollPane.getViewport());
    return scrollPane;
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
    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(ActionManager.getInstance().getAction(VcsLogActionPlaces.TOOLBAR_ACTION_GROUP));

    DefaultActionGroup mainGroup = new DefaultActionGroup();
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

    JPanel panel = new JPanel(new MigLayout("ins 0, fill", "[left]0[left, fill]push[right]"));
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

  public JComponent getMainComponent() {
    return this;
  }

  public void setBranchesPanelVisible(boolean visible) {
    JScrollPane scrollPane = UIUtil.getParentOfType(JScrollPane.class, myBranchesPanel);
    if (scrollPane != null) {
      scrollPane.setVisible(visible);
    }
    else {
      myBranchesPanel.setVisible(visible);
    }
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
      return myLogDataManager;
    }
    else if (VcsDataKeys.CHANGES.is(dataId) || VcsDataKeys.SELECTED_CHANGES.is(dataId)) {
      return ArrayUtil.toObjectArray(myChangesBrowser.getCurrentDisplayedChanges(), Change.class);
    }
    else if (VcsDataKeys.CHANGE_LISTS.is(dataId)) {
      List<VcsFullCommitDetails> details = myLog.getSelectedDetails();
      if (details.size() > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
      return ContainerUtil
        .map2Array(details, CommittedChangeListForRevision.class, new Function<VcsFullCommitDetails, CommittedChangeListForRevision>() {
          @Override
          public CommittedChangeListForRevision fun(@NotNull VcsFullCommitDetails details) {
            return new CommittedChangeListForRevision(details.getSubject(), details.getFullMessage(), VcsUserUtil.getShortPresentation(details.getCommitter()),
                                                      new Date(details.getCommitTime()), details.getChanges(),
                                                      convertToRevisionNumber(details.getId()));
          }
        });
    }
    else if (VcsDataKeys.VCS_REVISION_NUMBERS.is(dataId)) {
      List<CommitId> hashes = myLog.getSelectedCommits();
      if (hashes.size() > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
      return ArrayUtil.toObjectArray(ContainerUtil.map(hashes, new Function<CommitId, VcsRevisionNumber>() {
        @Override
        public VcsRevisionNumber fun(CommitId commitId) {
          return convertToRevisionNumber(commitId.getHash());
        }
      }), VcsRevisionNumber.class);
    }
    else if (VcsDataKeys.VCS.is(dataId)) {
      int[] selectedRows = myGraphTable.getSelectedRows();
      if (selectedRows.length == 0 || selectedRows.length > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
      Set<VirtualFile> roots = ContainerUtil.map2Set(Ints.asList(selectedRows), new Function<Integer, VirtualFile>() {
        @Override
        public VirtualFile fun(@NotNull Integer row) {
          return myGraphTable.getModel().getRoot(row);
        }
      });
      if (roots.size() == 1) {
        return myLogDataManager.getLogProvider(assertNotNull(getFirstItem(roots))).getSupportedVcs();
      }
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

  public void onFiltersChange(@NotNull VcsLogFilterCollection filters) {
    myBranchesPanel.onFiltersChange(filters);
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
    myLogDataManager.getMiniDetailsGetter().removeDetailsLoadedListener(myMiniDetailsLoadedListener);
    myLogDataManager.getCommitDetailsGetter().removeDetailsLoadedListener(myFullDetailsLoadedListener);
    myLogDataManager.getContainingBranchesGetter().removeTaskCompletedListener(myTaskCompletedListener);

    myDetailsSplitter.dispose();
    myChangesBrowserSplitter.dispose();
  }

  private class CommitSelectionListener implements ListSelectionListener {
    private final ChangesBrowser myChangesBrowser;
    private ProgressIndicator myLastRequest;

    public CommitSelectionListener(ChangesBrowser changesBrowser) {
      myChangesBrowser = changesBrowser;
    }

    @Override
    public void valueChanged(@Nullable ListSelectionEvent event) {
      if (event != null && event.getValueIsAdjusting()) return;

      if (myLastRequest != null) myLastRequest.cancel();
      myLastRequest = null;

      int rows = getGraphTable().getSelectedRowCount();
      if (rows < 1) {
        myChangesLoadingPane.stopLoading();
        myChangesBrowser.getViewer().setEmptyText("No commits selected");
        myChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
      }
      else {
        myChangesBrowser.setChangesToDisplay(Collections.<Change>emptyList());
        setDefaultEmptyText(myChangesBrowser);
        myChangesLoadingPane.startLoading();

        final EmptyProgressIndicator indicator = new EmptyProgressIndicator();
        myLastRequest = indicator;
        myLog.requestSelectedDetails(new Consumer<List<VcsFullCommitDetails>>() {
          @Override
          public void consume(List<VcsFullCommitDetails> detailsList) {
            if (myLastRequest == indicator && !(indicator.isCanceled())) {
              myLastRequest = null;
              List<Change> changes = ContainerUtil.newArrayList();
              List<VcsFullCommitDetails> detailsListReversed = ContainerUtil.reverse(detailsList);
              for (VcsFullCommitDetails details : detailsListReversed) {
                changes.addAll(details.getChanges());
              }
              changes = CommittedChangesTreeBrowser.zipChanges(changes);
              myChangesLoadingPane.stopLoading();
              myChangesBrowser.setChangesToDisplay(changes);
            }
          }
        }, indicator);
      }
    }
  }

  private class MyFocusPolicy extends ComponentsListFocusTraversalPolicy {
    @NotNull
    @Override
    protected List<Component> getOrderedComponents() {
      return Arrays.<Component>asList(myGraphTable, myChangesBrowser.getPreferredFocusedComponent(), myTextFilter.getTextEditor());
    }
  }
}
