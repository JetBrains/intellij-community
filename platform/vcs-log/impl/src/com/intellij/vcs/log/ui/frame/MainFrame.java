// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.google.common.primitives.Ints;
import com.intellij.diff.editor.VCSContentVirtualFile;
import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWindowHolder;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.DiffPreviewProvider;
import com.intellij.openapi.vcs.changes.PreviewDiffVirtualFile;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.navigation.History;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.DataPackBase;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogContentProvider;
import com.intellij.vcs.log.impl.VcsLogContentUtil;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.actions.IntelliSortChooserPopupAction;
import com.intellij.vcs.log.ui.actions.ShowPreviewEditorAction;
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx;
import com.intellij.vcs.log.ui.table.CommitSelectionListener;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.BekUtil;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.filters.VcsLogFiltersKt;
import kotlin.Unit;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.toVirtualFileArray;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.vcs.log.VcsLogDataKeys.VCS_LOG;
import static com.intellij.vcs.log.VcsLogDataKeys.VCS_LOG_UI;

public class MainFrame extends JPanel implements DataProvider, Disposable {
  private static final String DIFF_SPLITTER_PROPORTION = "vcs.log.diff.splitter.proportion";
  private static final String DETAILS_SPLITTER_PROPORTION = "vcs.log.details.splitter.proportion";
  private static final String CHANGES_SPLITTER_PROPORTION = "vcs.log.changes.splitter.proportion";

  @NotNull private final VcsLogData myLogData;
  @NotNull private final MainVcsLogUiProperties myUiProperties;

  @NotNull private final JComponent myToolbar;
  @NotNull private final VcsLogGraphTable myGraphTable;

  @NotNull private final VcsLogFilterUiEx myFilterUi;

  @NotNull private final VcsLogChangesBrowser myChangesBrowser;
  @NotNull private final Splitter myChangesBrowserSplitter;

  @NotNull private final VcsLogChangeProcessor myPreviewDiff;
  @NotNull private final Splitter myPreviewDiffSplitter;

  @NotNull private final VcsLogCommitDetailsListPanel myDetailsPanel;
  @NotNull private final Splitter myDetailsSplitter;
  @NotNull private final EditorNotificationPanel myNotificationLabel;
  @NotNull private final AbstractVcsLogUi myLogUi;

  @Nullable DiffPreviewProvider myDiffPreviewProvider;
  @Nullable private VCSContentVirtualFile myGraphViewFile;
  @NotNull private final JComponent myToolbarsAndTable;

  public MainFrame(@NotNull VcsLogData logData,
                   @NotNull AbstractVcsLogUi logUi,
                   @NotNull MainVcsLogUiProperties uiProperties,
                   @NotNull VcsLogFilterUiEx filterUi) {
    myLogData = logData;
    myUiProperties = uiProperties;

    myFilterUi = filterUi;
    myLogUi = logUi;

    myGraphTable = new MyVcsLogGraphTable(logUi, logData);
    myGraphTable.setCompactReferencesView(myUiProperties.get(MainVcsLogUiProperties.COMPACT_REFERENCES_VIEW));
    myGraphTable.setShowTagNames(myUiProperties.get(MainVcsLogUiProperties.SHOW_TAG_NAMES));
    myGraphTable.setLabelsLeftAligned(myUiProperties.get(MainVcsLogUiProperties.LABELS_LEFT_ALIGNED));

    PopupHandler.installPopupHandler(myGraphTable, VcsLogActionPlaces.POPUP_ACTION_GROUP, VcsLogActionPlaces.VCS_LOG_TABLE_PLACE);
    myDetailsPanel = new VcsLogCommitDetailsListPanel(logData, logUi.getColorManager(), this) {
      @Override
      protected void navigate(@NotNull CommitId commit) {
        logUi.jumpToCommit(commit.getHash(), commit.getRoot());
      }
    };

    myChangesBrowser = new VcsLogChangesBrowser(logData.getProject(), myUiProperties, (commitId) -> {
      int index = myLogData.getCommitIndex(commitId.getHash(), commitId.getRoot());
      return myLogData.getMiniDetailsGetter().getCommitData(index, Collections.singleton(index));
    }, this);
    myChangesBrowser.getDiffAction().registerCustomShortcutSet(myChangesBrowser.getDiffAction().getShortcutSet(), getGraphTable());
    JBLoadingPanel changesLoadingPane = new JBLoadingPanel(new BorderLayout(), this,
                                                           ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS);
    changesLoadingPane.add(myChangesBrowser);

    myPreviewDiff = new VcsLogChangeProcessor(logData.getProject(), myChangesBrowser, false, this);

    myToolbar = createActionsToolbar();
    myChangesBrowser.setToolbarHeightReferent(myToolbar);
    myPreviewDiff.getToolbarWrapper().setVerticalSizeReferent(myToolbar);

    MyCommitSelectionListenerForDiff listenerForDiff = new MyCommitSelectionListenerForDiff(changesLoadingPane);
    myGraphTable.getSelectionModel().addListSelectionListener(listenerForDiff);
    Disposer.register(this, () -> myGraphTable.getSelectionModel().removeListSelectionListener(listenerForDiff));

    myDetailsPanel.installCommitSelectionListener(myGraphTable);
    VcsLogUiUtil.installDetailsListeners(myGraphTable, myDetailsPanel, myLogData, this);

    myNotificationLabel = new EditorNotificationPanel(UIUtil.getPanelBackground());
    myNotificationLabel.setVisible(false);
    myNotificationLabel.setBorder(new CompoundBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                                                     notNull(myNotificationLabel.getBorder(), JBUI.Borders.empty())));

    JComponent toolbars = new JPanel(new BorderLayout());
    toolbars.add(myToolbar, BorderLayout.NORTH);
    toolbars.add(myNotificationLabel, BorderLayout.CENTER);
    myToolbarsAndTable = new JPanel(new BorderLayout());
    myToolbarsAndTable.add(toolbars, BorderLayout.NORTH);
    myToolbarsAndTable.add(VcsLogUiUtil.installProgress(VcsLogUiUtil.setupScrolledGraph(myGraphTable, SideBorder.TOP),
                                                        myLogData, logUi.getId(), this), BorderLayout.CENTER);

    myDetailsSplitter = new OnePixelSplitter(true, DETAILS_SPLITTER_PROPORTION, 0.7f);
    myDetailsSplitter.setFirstComponent(changesLoadingPane);
    showDetails(myUiProperties.get(CommonUiProperties.SHOW_DETAILS));

    myChangesBrowserSplitter = new OnePixelSplitter(false, CHANGES_SPLITTER_PROPORTION, 0.7f);
    installGraphView();
    myChangesBrowserSplitter.setSecondComponent(myDetailsSplitter);

    myPreviewDiffSplitter = new OnePixelSplitter(false, DIFF_SPLITTER_PROPORTION, 0.7f);
    myPreviewDiffSplitter.setHonorComponentsMinimumSize(false);
    myPreviewDiffSplitter.setFirstComponent(myChangesBrowserSplitter);
    showDiffPreview(myUiProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW));

    setLayout(new BorderLayout());
    add(myPreviewDiffSplitter);

    Disposer.register(logUi, this);
    myGraphTable.resetDefaultFocusTraversalKeys();
    setFocusCycleRoot(true);
    setFocusTraversalPolicy(new MyFocusPolicy());

    final Project project = myLogData.getProject();
    initPreviewInEditor(project);
  }

  private void initPreviewInEditor(Project project) {
    if (!Registry.is("show.diff.preview.as.editor.tab")) {
      return;
    }

    myDiffPreviewProvider = new DiffPreviewProvider() {
      @NotNull
      @Override
      public DiffRequestProcessor createDiffRequestProcessor() {
        VcsLogChangeProcessor preview = new VcsLogChangeProcessor(project, myChangesBrowser, true,
                                                                  myChangesBrowser);
        preview.updatePreview(true);
        return preview;
      }

      @NotNull
      @Override
      public Object getOwner() {
        return MainFrame.this;
      }

      @Override
      public String getEditorTabName() {
        return "Repository Diff";
      }
    };

    myChangesBrowser.getViewer().addSelectionListener(() -> {
      if (myUiProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW) && !myChangesBrowser.getSelectedChanges().isEmpty()) {
        FileEditorManager instance = FileEditorManager.getInstance(project);
        PreviewDiffVirtualFile file = new PreviewDiffVirtualFile(myDiffPreviewProvider);
        instance.openFile(file, false, true);
      }
    }, this);

    new AnAction() {
      {
        setShortcutSet(CommonShortcuts.ESCAPE);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        openLogEditorTab();
      }
    }.registerCustomShortcutSet(myChangesBrowser, this);
  }

  private void installGraphView() {
    if (Registry.is("show.log.as.editor.tab")) {
      DataManager.registerDataProvider(myToolbarsAndTable, this);

      ApplicationManager.getApplication().invokeLater(() -> {
        openLogEditorTab();
      }, ModalityState.NON_MODAL);
    }
    else {
      myChangesBrowserSplitter.setFirstComponent(myToolbarsAndTable);
    }
  }

  public void openLogEditorTab() {
    VirtualFile file = getOrCreateGraphViewFile();

    FileEditor[] editors = FileEditorManager.getInstance(myLogData.getProject()).openFile(file, true);
    assert editors.length == 1 : "opened multiple log editors for " + file;
    FileEditor editor = editors[0];
    final JComponent component = editor.getComponent();
    final EditorWindowHolder holder =
      ComponentUtil.getParentOfType((Class<? extends EditorWindowHolder>)EditorWindowHolder.class, (Component)component);
    if (holder == null) {
      return;
    }
    EditorWindow editorWindow = holder.getEditorWindow();
    editorWindow.setFilePinned(file, true);
  }

  private VirtualFile getOrCreateGraphViewFile() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myGraphViewFile == null || !myGraphViewFile.isValid()) {
      myGraphViewFile = new VCSContentVirtualFile(myToolbarsAndTable, () -> {
        return getTabName();
      });

      myGraphViewFile.putUserData(VCSContentVirtualFile.TabSelector, () -> {

        VcsLogContentUtil.findAndSelect(myLogData.getProject(), AbstractVcsLogUi.class, ui1 -> {
          return ui1.getId() == myLogUi.getId();
        });

        return Unit.INSTANCE;
      });
    }

    Disposer.register(this, () -> myGraphViewFile = null);

    return myGraphViewFile;
  }

  //todo move it out from MainFrame
  private String getTabName() {
    VcsLogContentProvider instance = VcsLogContentProvider.getInstance(myLogData.getProject());
    if (instance != null) {
      VcsLogUiImpl ui = instance.getUi();
      if (ui != null && ui.getMainFrame() == this) {
        return VcsLogContentProvider.TAB_NAME;
      }
    }

    String name = "all";
    VcsLogFilterCollection filters = myFilterUi.getFilters();
    if (!filters.isEmpty()) {
      name = StringUtil.shortenTextWithEllipsis(VcsLogFiltersKt.getPresentation(filters), 150, 20);
    }

    return ContentUtilEx.getFullName(VcsLogContentProvider.TAB_NAME, name);
  }

  public void setExplanationHtml(@Nullable String text) {
    myNotificationLabel.setText(text);
    myNotificationLabel.setVisible(text != null);
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
    myChangesBrowser.setAffectedPaths(VcsLogUtil.getAffectedPaths(dataPack));
  }

  @NotNull
  public VcsLogGraphTable getGraphTable() {
    return myGraphTable;
  }

  @NotNull
  public VcsLogFilterUiEx getFilterUi() {
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

    Wrapper textFilter = new Wrapper(myFilterUi.getTextFilterComponent());
    textFilter.setVerticalSizeReferent(toolbar.getComponent());
    textFilter.setBorder(JBUI.Borders.emptyLeft(5));

    DefaultActionGroup rightCornerGroup =
      new DefaultActionGroup(ActionManager.getInstance().getAction(VcsLogActionPlaces.TOOLBAR_RIGHT_CORNER_ACTION_GROUP));
    ActionToolbar rightCornerToolbar = createActionsToolbar(rightCornerGroup);
    rightCornerToolbar.setReservePlaceAutoPopupIcon(false);
    rightCornerToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);

    JPanel panel = new JPanel(new MigLayout("ins 0, fill", "[left]0[left, fill]push[right]", "center"));
    panel.add(textFilter);
    panel.add(toolbar.getComponent());
    panel.add(rightCornerToolbar.getComponent());
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
      return myChangesBrowser.getDirectChanges().toArray(new Change[0]);
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
      return myLogData.getLogProvider(notNull(getFirstItem(roots))).getDiffHandler();
    }
    else if (ShowPreviewEditorAction.DATA_KEY.is(dataId)) {
      return myDiffPreviewProvider;
    }
    else if (VCS_LOG.is(dataId)) {
      return myLogUi.getVcsLog();
    }
    else if (VCS_LOG_UI.is(dataId)) {
      return myLogUi;
    }
    else if (VcsDataKeys.VCS_REVISION_NUMBER.is(dataId)) {
      List<CommitId> hashes = myLogUi.getVcsLog().getSelectedCommits();
      if (hashes.isEmpty()) return null;
      return VcsLogUtil.convertToRevisionNumber(notNull(getFirstItem(hashes)).getHash());
    }
    else if (VcsDataKeys.VCS_REVISION_NUMBERS.is(dataId)) {
      List<CommitId> hashes = myLogUi.getVcsLog().getSelectedCommits();
      if (hashes.size() > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
      return ContainerUtil.map(hashes,
                               commitId -> VcsLogUtil.convertToRevisionNumber(commitId.getHash())).toArray(new VcsRevisionNumber[0]);
    }
    else if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return myLogUi.getHelpId();
    }
    else if (History.KEY.is(dataId)) {
      return myLogUi.getNavigationHistory();
    }

    return null;
  }

  @NotNull
  private Collection<VirtualFile> getSelectedRoots() {
    Collection<VirtualFile> roots = myLogData.getRoots();
    if (roots.size() == 1) return roots;
    int[] selectedRows = myGraphTable.getSelectedRows();
    if (selectedRows.length == 0 || selectedRows.length > VcsLogUtil.MAX_SELECTED_COMMITS) {
      return VcsLogUtil.getAllVisibleRoots(roots, myFilterUi.getFilters());
    }
    return ContainerUtil.map2Set(Ints.asList(selectedRows), row -> myGraphTable.getModel().getRootAtRow(row));
  }

  @NotNull
  public JComponent getToolbar() {
    return myToolbar;
  }

  public void showDetails(boolean state) {
    myDetailsSplitter.setSecondComponent(state ? myDetailsPanel : null);
  }

  public void showDiffPreview(boolean state) {
    myPreviewDiff.updatePreview(state);
    if (Registry.is("show.diff.preview.as.editor.tab")) {
      if (myDiffPreviewProvider != null) {
        if (!state) {
          //'equals' for such files is overridden and means the equality of its owner
          FileEditorManager.getInstance(myLogData.getProject()).closeFile(new PreviewDiffVirtualFile(myDiffPreviewProvider));
        }
        else {
          FileEditorManager.getInstance(myLogData.getProject()).openFile(new PreviewDiffVirtualFile(myDiffPreviewProvider), false, true);
        }
      }

      return;
    }

    myPreviewDiffSplitter.setSecondComponent(state ? myPreviewDiff.getComponent() : null);
  }

  @Override
  public void dispose() {
    myDetailsSplitter.dispose();
    myChangesBrowserSplitter.dispose();
  }

  private class MyCommitSelectionListenerForDiff extends CommitSelectionListener<VcsFullCommitDetails> {
    @NotNull private final JBLoadingPanel myChangesLoadingPane;

    protected MyCommitSelectionListenerForDiff(@NotNull JBLoadingPanel changesLoadingPane) {
      super(MainFrame.this.myGraphTable, myLogData.getCommitDetailsGetter());
      myChangesLoadingPane = changesLoadingPane;
    }

    @Override
    protected void onEmptySelection() {
      myChangesBrowser.setSelectedDetails(Collections.emptyList());
    }

    @Override
    protected void onDetailsLoaded(@NotNull List<? extends VcsFullCommitDetails> detailsList) {
      int maxSize = VcsLogUtil.getMaxSize(detailsList);
      if (maxSize > VcsLogUtil.getShownChangesLimit()) {
        String commitText = detailsList.size() == 1 ? "This commit" : "One of the selected commits";
        String sizeText = VcsLogUtil.getSizeText(maxSize);
        myChangesBrowser.showText(statusText -> {
          statusText.setText(commitText + " has " + sizeText + " changes");
          statusText.appendSecondaryText("Show anyway", VcsLogUiUtil.getLinkAttributes(),
                                         e -> myChangesBrowser.setSelectedDetails(detailsList));
        });
      }
      else {
        myChangesBrowser.setSelectedDetails(detailsList);
      }
    }

    @Override
    protected void onSelection(@NotNull int[] selection) {
      if (Registry.is("show.log.as.editor.tab")) {
        ChangesViewContentManager.getInstance(myLogData.getProject()).selectContent("Repository");
      }

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
      myChangesBrowser.showText(statusText -> statusText.setText("Error loading commits"));
    }
  }

  private class MyFocusPolicy extends ComponentsListFocusTraversalPolicy {
    @NotNull
    @Override
    protected List<Component> getOrderedComponents() {
      return Arrays.asList(myGraphTable,
                           myChangesBrowser.getPreferredFocusedComponent(),
                           myPreviewDiff.getPreferredFocusedComponent(),
                           myFilterUi.getTextFilterComponent().getTextEditor());
    }
  }

  private class MyVcsLogGraphTable extends VcsLogGraphTable {
    @NotNull private final Runnable myRefresh;

    MyVcsLogGraphTable(@NotNull AbstractVcsLogUi ui, @NotNull VcsLogData logData) {
      super(ui, logData, ui::requestMore);
      myRefresh = () -> ui.getRefresher().onRefresh();
    }

    @Override
    protected void updateEmptyText() {
      StatusText statusText = getEmptyText();
      VisiblePack visiblePack = getModel().getVisiblePack();

      DataPackBase dataPack = visiblePack.getDataPack();
      if (dataPack instanceof DataPack.ErrorDataPack) {
        setErrorEmptyText(((DataPack.ErrorDataPack)dataPack).getError(), "Error loading commits");
        appendActionToEmptyText("Refresh", () -> myLogData.refresh(myLogData.getLogProviders().keySet()));
      }
      else if (visiblePack instanceof VisiblePack.ErrorVisiblePack) {
        setErrorEmptyText(((VisiblePack.ErrorVisiblePack)visiblePack).getError(), "Error filtering commits");
        if (visiblePack.getFilters().isEmpty()) {
          appendActionToEmptyText("Refresh", myRefresh);
        }
        else {
          VcsLogUiUtil.appendResetFiltersActionToEmptyText(myFilterUi, getEmptyText());
        }
      }
      else if (visiblePack.getVisibleGraph().getVisibleCommitCount() == 0) {
        if (visiblePack.getFilters().isEmpty()) {
          statusText.setText("No changes committed.").
            appendSecondaryText("Commit local changes", VcsLogUiUtil.getLinkAttributes(),
                                ActionUtil.createActionListener(VcsLogActionPlaces.CHECKIN_PROJECT_ACTION, this,
                                                                ActionPlaces.UNKNOWN));
          String shortcutText = KeymapUtil.getFirstKeyboardShortcutText(VcsLogActionPlaces.CHECKIN_PROJECT_ACTION);
          if (!shortcutText.isEmpty()) {
            statusText.appendSecondaryText(" (" + shortcutText + ")", StatusText.DEFAULT_ATTRIBUTES, null);
          }
        }
        else {
          myFilterUi.setCustomEmptyText(getEmptyText());
        }
      }
      else {
        statusText.setText(CHANGES_LOG_TEXT);
      }
    }
  }
}
