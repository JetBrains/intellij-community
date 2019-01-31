package com.intellij.vcs.log.ui.frame;

import com.google.common.primitives.Ints;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import com.intellij.vcs.CommittedChangeListForRevision;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogFilterUi;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.actions.IntelliSortChooserPopupAction;
import com.intellij.vcs.log.ui.filter.VcsLogClassicFilterUi;
import com.intellij.vcs.log.ui.table.CommitSelectionListener;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.BekUtil;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.toVirtualFileArray;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class MainFrame extends JPanel implements DataProvider, Disposable {
  private static final String DIFF_SPLITTER_PROPORTION = "vcs.log.diff.splitter.proportion";
  private static final String DETAILS_SPLITTER_PROPORTION = "vcs.log.details.splitter.proportion";
  private static final String CHANGES_SPLITTER_PROPORTION = "vcs.log.changes.splitter.proportion";

  @NotNull private final VcsLogData myLogData;
  @NotNull private final AbstractVcsLogUi myUi;
  @NotNull private final VcsLog myLog;
  @NotNull private final VcsLogClassicFilterUi myFilterUi;

  @NotNull private final JBLoadingPanel myChangesLoadingPane;
  @NotNull private final VcsLogGraphTable myGraphTable;
  @NotNull private final DetailsPanel myDetailsPanel;
  @NotNull private final Splitter myDetailsSplitter;
  @NotNull private final JComponent myToolbar;
  @NotNull private final VcsLogChangesBrowser myChangesBrowser;
  @NotNull private final Splitter myChangesBrowserSplitter;
  @NotNull private final Splitter myPreviewDiffSplitter;
  @NotNull private final SearchTextField myTextFilter;
  @NotNull private final MainVcsLogUiProperties myUiProperties;
  @NotNull private final MyCommitSelectionListenerForDiff mySelectionListenerForDiff;
  @NotNull private final VcsLogChangeProcessor myPreviewDiff;

  public MainFrame(@NotNull VcsLogData logData,
                   @NotNull VcsLogUiImpl ui,
                   @NotNull MainVcsLogUiProperties uiProperties,
                   @NotNull VcsLog log,
                   @NotNull VisiblePack initialDataPack) {
    // collect info
    myLogData = logData;
    myUi = ui;
    myLog = log;
    myUiProperties = uiProperties;

    myFilterUi = new VcsLogClassicFilterUi(ui, logData, myUiProperties, initialDataPack);

    // initialize components
    myGraphTable = new MyVcsLogGraphTable(ui, logData, initialDataPack);
    myGraphTable.setCompactReferencesView(myUiProperties.get(MainVcsLogUiProperties.COMPACT_REFERENCES_VIEW));
    myGraphTable.setShowTagNames(myUiProperties.get(MainVcsLogUiProperties.SHOW_TAG_NAMES));
    PopupHandler.installPopupHandler(myGraphTable, VcsLogActionPlaces.POPUP_ACTION_GROUP, VcsLogActionPlaces.VCS_LOG_TABLE_PLACE);
    myDetailsPanel = new DetailsPanel(logData, ui.getColorManager(), this) {
      @Override
      protected void navigate(@NotNull CommitId commit) {
        myUi.jumpToCommit(commit.getHash(), commit.getRoot());
      }
    };

    myChangesBrowser = new VcsLogChangesBrowser(logData.getProject(), myUiProperties, (commitId) -> {
      int index = myLogData.getCommitIndex(commitId.getHash(), commitId.getRoot());
      return myLogData.getMiniDetailsGetter().getCommitData(index, Collections.singleton(index));
    }, this);
    myChangesBrowser.getDiffAction().registerCustomShortcutSet(myChangesBrowser.getDiffAction().getShortcutSet(), getGraphTable());
    myChangesLoadingPane = new JBLoadingPanel(new BorderLayout(), this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS);
    myChangesLoadingPane.add(myChangesBrowser);

    myPreviewDiff = new VcsLogChangeProcessor(logData.getProject(), myChangesBrowser, this);

    myTextFilter = myFilterUi.createTextFilter();
    myToolbar = createActionsToolbar();
    myChangesBrowser.setToolbarHeightReferent(myToolbar);
    myPreviewDiff.getToolbarWrapper().setVerticalSizeReferent(myToolbar);

    Runnable changesListener = () -> ApplicationManager.getApplication().invokeLater(
      () -> myPreviewDiff.updatePreview(myUiProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW)));
    myChangesBrowser.getViewer().addSelectionListener(changesListener);
    myChangesBrowser.setModelUpdateListener(changesListener);

    mySelectionListenerForDiff = new MyCommitSelectionListenerForDiff();
    myGraphTable.getSelectionModel().addListSelectionListener(mySelectionListenerForDiff);
    myDetailsPanel.installCommitSelectionListener(myGraphTable);
    VcsLogUiUtil.installDetailsListeners(myGraphTable, myDetailsPanel, myLogData, this);

    JComponent toolbars = new JPanel(new BorderLayout());
    toolbars.add(myToolbar, BorderLayout.NORTH);
    JComponent toolbarsAndTable = new JPanel(new BorderLayout());
    toolbarsAndTable.add(toolbars, BorderLayout.NORTH);
    toolbarsAndTable.add(VcsLogUiUtil.installProgress(VcsLogUiUtil.setupScrolledGraph(myGraphTable, SideBorder.TOP),
                                                      myLogData, ui.getId(), this), BorderLayout.CENTER);

    myDetailsSplitter = new OnePixelSplitter(true, DETAILS_SPLITTER_PROPORTION, 0.7f);
    myDetailsSplitter.setFirstComponent(myChangesLoadingPane);
    showDetails(myUiProperties.get(CommonUiProperties.SHOW_DETAILS));

    myChangesBrowserSplitter = new OnePixelSplitter(false, CHANGES_SPLITTER_PROPORTION, 0.7f);
    myChangesBrowserSplitter.setFirstComponent(toolbarsAndTable);
    myChangesBrowserSplitter.setSecondComponent(myDetailsSplitter);

    myPreviewDiffSplitter = new OnePixelSplitter(false, DIFF_SPLITTER_PROPORTION, 0.7f);
    myPreviewDiffSplitter.setHonorComponentsMinimumSize(false);
    myPreviewDiffSplitter.setFirstComponent(myChangesBrowserSplitter);
    showDiffPreview(myUiProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW));

    setLayout(new BorderLayout());
    add(myPreviewDiffSplitter);

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
    toolbarGroup.copyFromGroup((DefaultActionGroup)ActionManager.getInstance().getAction(VcsLogActionPlaces.TOOLBAR_ACTION_GROUP));
    if (BekUtil.isBekEnabled()) {
      Constraints constraint = new Constraints(Anchor.BEFORE, VcsLogActionPlaces.PRESENTATION_SETTINGS_ACTION_GROUP);
      if (BekUtil.isLinearBekEnabled()) {
        toolbarGroup.add(new IntelliSortChooserPopupAction(), constraint);
        // can not register both of the actions in xml file, choosing to register an action for the "outer world"
        // I can of course if linear bek is enabled replace the action on start but why bother
      }
      else {
        toolbarGroup.add(ActionManager.getInstance().getAction(VcsLogActionPlaces.VCS_LOG_INTELLI_SORT_ACTION),
                         constraint);
      }
    }

    DefaultActionGroup mainGroup = new DefaultActionGroup();
    mainGroup.add(ActionManager.getInstance().getAction(VcsLogActionPlaces.TEXT_FILTER_SETTINGS_ACTION_GROUP));
    mainGroup.add(new Separator());
    mainGroup.add(myFilterUi.createActionGroup());
    mainGroup.addSeparator();
    mainGroup.add(toolbarGroup);
    ActionToolbar toolbar = createActionsToolbar(mainGroup);

    Wrapper textFilter = new Wrapper(myTextFilter);
    textFilter.setVerticalSizeReferent(toolbar.getComponent());
    textFilter.setBorder(JBUI.Borders.emptyLeft(5));

    ActionToolbar goToHashOrRefAction =
      createActionsToolbar(
        new DefaultActionGroup(ActionManager.getInstance().getAction(VcsLogActionPlaces.VCS_LOG_GO_TO_HASH_OR_REF_ACTION)));
    goToHashOrRefAction.setReservePlaceAutoPopupIcon(false);
    goToHashOrRefAction.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);

    JPanel panel = new JPanel(new MigLayout("ins 0, fill", "[left]0[left, fill]push[right]", "center"));
    panel.add(textFilter);
    panel.add(toolbar.getComponent());
    panel.add(goToHashOrRefAction.getComponent());
    return panel;
  }

  @NotNull
  private ActionToolbar createActionsToolbar(@NotNull DefaultActionGroup mainGroup) {
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(VcsLogActionPlaces.VCS_LOG_TOOLBAR_PLACE, mainGroup, true);
    toolbar.setTargetComponent(this);
    return toolbar;
  }

  @NotNull
  public JComponent getMainComponent() {
    return this;
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (VcsDataKeys.CHANGES.is(dataId) || VcsDataKeys.SELECTED_CHANGES.is(dataId)) {
      return ArrayUtil.toObjectArray(myChangesBrowser.getDirectChanges(), Change.class);
    }
    else if (VcsDataKeys.CHANGE_LISTS.is(dataId)) {
      List<VcsFullCommitDetails> details = myLog.getSelectedDetails();
      if (details.size() > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
      return ContainerUtil.map2Array(details, CommittedChangeListForRevision.class, VcsLogUtil::createCommittedChangeList);
    }
    else if (VcsLogInternalDataKeys.LOG_UI_PROPERTIES.is(dataId)) {
      return myUiProperties;
    }
    else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) {
      Collection<VirtualFile> roots = getSelectedRoots();
      return toVirtualFileArray(roots);
    }
    else if (VcsLogInternalDataKeys.LOG_DIFF_HANDLER.is(dataId)) {
      Collection<VirtualFile> roots = getSelectedRoots();
      if (roots.size() != 1) return null;
      return myUi.getLogData().getLogProvider(notNull(getFirstItem(roots))).getDiffHandler();
    }
    return null;
  }

  @NotNull
  private Collection<VirtualFile> getSelectedRoots() {
    if (myUi.getLogData().getRoots().size() == 1) return myUi.getLogData().getRoots();
    int[] selectedRows = myGraphTable.getSelectedRows();
    if (selectedRows.length == 0 || selectedRows.length > VcsLogUtil.MAX_SELECTED_COMMITS) return VcsLogUtil.getVisibleRoots(myUi);
    return ContainerUtil.map2Set(Ints.asList(selectedRows), row -> myGraphTable.getModel().getRoot(row));
  }

  @NotNull
  public JComponent getToolbar() {
    return myToolbar;
  }

  @NotNull
  public SearchTextField getTextFilter() {
    return myTextFilter;
  }

  public void showDetails(boolean state) {
    myDetailsSplitter.setSecondComponent(state ? myDetailsPanel : null);
  }

  public void showDiffPreview(boolean state) {
    myPreviewDiff.updatePreview(state);
    myPreviewDiffSplitter.setSecondComponent(state ? myPreviewDiff.getComponent() : null);
  }

  @Override
  public void dispose() {
    myGraphTable.getSelectionModel().removeListSelectionListener(mySelectionListenerForDiff);
    myDetailsSplitter.dispose();
    myChangesBrowserSplitter.dispose();
  }

  private class MyCommitSelectionListenerForDiff extends CommitSelectionListener<VcsFullCommitDetails> {
    protected MyCommitSelectionListenerForDiff() {
      super(MainFrame.this.myGraphTable, myLogData.getCommitDetailsGetter());
    }

    @Override
    protected void onEmptySelection() {
      myChangesBrowser.setSelectedDetails(Collections.emptyList());
    }

    @Override
    protected void onDetailsLoaded(@NotNull List<? extends VcsFullCommitDetails> detailsList) {
      myChangesBrowser.setSelectedDetails(detailsList);
    }

    @Override
    protected void onSelection(@NotNull int[] selection) {
      myChangesBrowser.resetSelectedDetails();
    }

    @Override
    protected void startLoading() {
      myChangesLoadingPane.startLoading();
    }

    @Override
    protected void stopLoading() {
      myChangesLoadingPane.stopLoading();
    }

    @Override
    protected void onError(@NotNull Throwable error) {
      myChangesBrowser.setSelectedDetails(Collections.emptyList());
      myChangesBrowser.getViewer().setEmptyText("Error loading commits");
    }
  }

  private class MyFocusPolicy extends ComponentsListFocusTraversalPolicy {
    @NotNull
    @Override
    protected List<Component> getOrderedComponents() {
      return Arrays.asList(myGraphTable, myChangesBrowser.getPreferredFocusedComponent(), myTextFilter.getTextEditor());
    }
  }

  private class MyVcsLogGraphTable extends VcsLogGraphTable {
    MyVcsLogGraphTable(@NotNull VcsLogUiImpl ui, @NotNull VcsLogData logData, @NotNull VisiblePack initialDataPack) {
      super(ui, logData, initialDataPack, ui::requestMore);
    }

    @Override
    protected void updateEmptyText() {
      StatusText statusText = getEmptyText();
      VisiblePack visiblePack = getModel().getVisiblePack();

      if (visiblePack.getVisibleGraph().getVisibleCommitCount() == 0) {
        if (visiblePack.getFilters().isEmpty()) {
          statusText.setText("No changes committed.").
            appendSecondaryText("Commit local changes", VcsLogUiUtil.getLinkAttributes(),
                                ActionUtil.createActionListener(VcsLogActionPlaces.CHECKIN_PROJECT_ACTION, this, ActionPlaces.UNKNOWN));
          String shortcutText = KeymapUtil.getFirstKeyboardShortcutText(VcsLogActionPlaces.CHECKIN_PROJECT_ACTION);
          if (!shortcutText.isEmpty()) {
            statusText.appendSecondaryText(" (" + shortcutText + ")", StatusText.DEFAULT_ATTRIBUTES, null);
          }
        }
        else {
          statusText.setText("No commits matching filters.").appendSecondaryText("Reset filters", VcsLogUiUtil.getLinkAttributes(),
                                                                                 e -> myFilterUi.setFilter(null));
        }
      }
      else {
        statusText.setText(CHANGES_LOG_TEXT);
      }
    }
  }
}
