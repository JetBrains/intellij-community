// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame;

import com.google.common.primitives.Ints;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.navigation.History;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.DataPackBase;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogNavigationUtil;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogActionIds;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.actions.IntelliSortChooserPopupAction;
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel;
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel;
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx;
import com.intellij.vcs.log.ui.table.CommitSelectionListener;
import com.intellij.vcs.log.ui.table.IndexSpeedSearch;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.BekUtil;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import kotlin.Unit;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

import static com.intellij.openapi.vfs.VfsUtilCore.toVirtualFileArray;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class MainFrame extends JPanel implements DataProvider, Disposable {
  private static final @NonNls String DIFF_SPLITTER_PROPORTION = "vcs.log.diff.splitter.proportion";
  private static final @NonNls String DETAILS_SPLITTER_PROPORTION = "vcs.log.details.splitter.proportion";
  private static final @NonNls String CHANGES_SPLITTER_PROPORTION = "vcs.log.changes.splitter.proportion";
  private static final @NonNls String HELP_ID = "reference.changesToolWindow.log";

  private final @NotNull VcsLogData myLogData;
  private final @NotNull MainVcsLogUiProperties myUiProperties;

  private final @NotNull JComponent myToolbar;
  private final @NotNull VcsLogGraphTable myGraphTable;

  private final @NotNull VcsLogFilterUiEx myFilterUi;

  private final @NotNull VcsLogChangesBrowser myChangesBrowser;
  private final @NotNull Splitter myChangesBrowserSplitter;

  private final @NotNull CommitDetailsListPanel myDetailsPanel;
  private final @NotNull Splitter myDetailsSplitter;
  private final @NotNull EditorNotificationPanel myNotificationLabel;

  private final @NotNull History myHistory;

  private boolean myIsLoading;
  private @Nullable FilePath myPathToSelect = null;

  private final @NotNull FrameDiffPreview<VcsLogChangeProcessor> myDiffPreview;

  public MainFrame(@NotNull VcsLogData logData,
                   @NotNull AbstractVcsLogUi logUi,
                   @NotNull MainVcsLogUiProperties uiProperties,
                   @NotNull VcsLogFilterUiEx filterUi,
                   @NotNull VcsLogColorManager colorManager,
                   boolean withEditorDiffPreview,
                   @NotNull Disposable disposable) {
    myLogData = logData;
    myUiProperties = uiProperties;

    myFilterUi = filterUi;

    myGraphTable = new MyVcsLogGraphTable(logUi.getId(), logData, logUi.getProperties(), colorManager,
                                          () -> logUi.getRefresher().onRefresh(), logUi::requestMore, disposable);
    String vcsDisplayName = VcsLogUtil.getVcsDisplayName(logData.getProject(), logData.getLogProviders().values());
    myGraphTable.getAccessibleContext().setAccessibleName(VcsLogBundle.message("vcs.log.table.accessible.name", vcsDisplayName));

    PopupHandler.installPopupMenu(myGraphTable, VcsLogActionIds.POPUP_ACTION_GROUP, ActionPlaces.VCS_LOG_TABLE_PLACE);

    myDetailsPanel = new CommitDetailsListPanel(logData.getProject(), this, () -> {
      return new CommitDetailsPanel(commit -> {
        VcsLogNavigationUtil.jumpToCommit(logUi, commit.getHash(), commit.getRoot(), false, true);
        return Unit.INSTANCE;
      });
    });
    VcsLogCommitSelectionListenerForDetails.install(myGraphTable, myDetailsPanel, this);

    myChangesBrowser = new VcsLogChangesBrowser(logData.getProject(), myUiProperties, (commitId) -> {
      int index = myLogData.getCommitIndex(commitId.getHash(), commitId.getRoot());
      return myLogData.getMiniDetailsGetter().getCachedDataOrPlaceholder(index);
    }, withEditorDiffPreview, this);
    myChangesBrowser.getDiffAction().registerCustomShortcutSet(myChangesBrowser.getDiffAction().getShortcutSet(), getGraphTable());
    JBLoadingPanel changesLoadingPane = new JBLoadingPanel(new BorderLayout(), this,
                                                           ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
      @Override
      public Dimension getMinimumSize() {
        return VcsLogUiUtil.expandToFitToolbar(super.getMinimumSize(), myChangesBrowser.getToolbar().getComponent());
      }
    };
    changesLoadingPane.add(myChangesBrowser);

    myToolbar = createActionsToolbar();
    myChangesBrowser.setToolbarHeightReferent(myToolbar);

    MyCommitSelectionListenerForDiff listenerForDiff = new MyCommitSelectionListenerForDiff(changesLoadingPane);
    myGraphTable.getSelectionModel().addListSelectionListener(listenerForDiff);
    Disposer.register(this, () -> myGraphTable.getSelectionModel().removeListSelectionListener(listenerForDiff));

    myNotificationLabel = new EditorNotificationPanel(UIUtil.getPanelBackground(), EditorNotificationPanel.Status.Warning);
    myNotificationLabel.setVisible(false);
    myNotificationLabel.setBorder(new CompoundBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                                                     notNull(myNotificationLabel.getBorder(), JBUI.Borders.empty())));

    JComponent toolbars = new JPanel(new BorderLayout());
    toolbars.add(myToolbar, BorderLayout.NORTH);
    toolbars.add(myNotificationLabel, BorderLayout.CENTER);
    JComponent toolbarsAndTable = new JPanel(new BorderLayout());
    toolbarsAndTable.add(toolbars, BorderLayout.NORTH);
    JScrollPane scrollPane = VcsLogUiUtil.setupScrolledGraph(myGraphTable, SideBorder.NONE);
    JComponent progress = VcsLogUiUtil.installProgress(scrollPane, myLogData, logUi.getId(), this);
    toolbarsAndTable.add(progress, BorderLayout.CENTER);
    ScrollableContentBorder.setup(scrollPane, Side.TOP, progress);
    myDetailsSplitter = new OnePixelSplitter(true, DETAILS_SPLITTER_PROPORTION, 0.7f);
    myDetailsSplitter.setFirstComponent(changesLoadingPane);
    showDetails(myUiProperties.get(CommonUiProperties.SHOW_DETAILS));

    myChangesBrowserSplitter = new OnePixelSplitter(false, CHANGES_SPLITTER_PROPORTION, 0.7f);
    myChangesBrowserSplitter.setFirstComponent(toolbarsAndTable);
    myChangesBrowserSplitter.setSecondComponent(myDetailsSplitter);

    setLayout(new BorderLayout());
    VcsLogChangeProcessor processor = myChangesBrowser.createChangeProcessor(false);
    processor.getToolbarWrapper().setVerticalSizeReferent(getToolbar());
    myDiffPreview = new FrameDiffPreview<>(processor,
                                           myUiProperties, myChangesBrowserSplitter, DIFF_SPLITTER_PROPORTION,
                                           myUiProperties.get(MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT),
                                           0.7f) {
      @Override
      public void updatePreview(boolean state) {
        getPreviewDiff().updatePreview(state);
      }
    };
    add(myDiffPreview.getMainComponent());

    myHistory = VcsLogUiUtil.installNavigationHistory(logUi, myGraphTable);

    Disposer.register(disposable, this);

    myGraphTable.resetDefaultFocusTraversalKeys();
    setFocusCycleRoot(true);
    setFocusTraversalPolicy(new MyFocusPolicy());
  }

  public void setExplanationHtml(@Nullable @NlsContexts.LinkLabel String text) {
    myNotificationLabel.setText(Objects.requireNonNullElse(text, ""));
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

  public @NotNull VcsLogGraphTable getGraphTable() {
    return myGraphTable;
  }

  public @NotNull VcsLogFilterUiEx getFilterUi() {
    return myFilterUi;
  }

  protected @NotNull JComponent createActionsToolbar() {
    ActionManager actionManager = ActionManager.getInstance();

    DefaultActionGroup toolbarGroup = (DefaultActionGroup)actionManager.getAction(VcsLogActionIds.TOOLBAR_ACTION_GROUP);

    DefaultActionGroup mainGroup = new DefaultActionGroup();
    mainGroup.add(myFilterUi.createActionGroup());
    mainGroup.addSeparator();
    mainGroup.add(toolbarGroup);
    ActionToolbar toolbar = actionManager.createActionToolbar(ActionPlaces.VCS_LOG_TOOLBAR_PLACE, mainGroup, true);
    toolbar.setTargetComponent(this);

    Wrapper textFilter = new Wrapper(myFilterUi.getTextFilterComponent().getComponent());
    textFilter.setVerticalSizeReferent(toolbar.getComponent());
    String vcsDisplayName = VcsLogUtil.getVcsDisplayName(myLogData.getProject(), myLogData.getLogProviders().values());
    textFilter.getAccessibleContext().setAccessibleName(VcsLogBundle.message("vcs.log.text.filter.accessible.name", vcsDisplayName));

    DefaultActionGroup presentationSettingsGroup = (DefaultActionGroup)actionManager.getAction(VcsLogActionIds.PRESENTATION_SETTINGS_ACTION_GROUP);
    configureIntelliSortAction(presentationSettingsGroup);

    ActionGroup rightCornerGroup = (ActionGroup)Objects.requireNonNull(CustomActionsSchema.getInstance().getCorrectedAction(VcsLogActionIds.TOOLBAR_RIGHT_CORNER_ACTION_GROUP));
    ActionToolbar rightCornerToolbar = actionManager.createActionToolbar(ActionPlaces.VCS_LOG_TOOLBAR_PLACE, rightCornerGroup, true);
    rightCornerToolbar.setTargetComponent(this);
    rightCornerToolbar.setReservePlaceAutoPopupIcon(false);

    JPanel panel = new JPanel(new MigLayout("ins 0, fill", "[left]0[left, fill]push[pref:pref, right]", "center"));
    GuiUtils.installVisibilityReferent(panel, toolbar.getComponent());
    panel.add(textFilter);
    panel.add(toolbar.getComponent());
    panel.add(rightCornerToolbar.getComponent());
    return panel;
  }

  private static void configureIntelliSortAction(@NotNull DefaultActionGroup group) {
    AnAction intelliSortAction = ActionManager.getInstance().getAction(VcsLogActionIds.VCS_LOG_INTELLI_SORT_ACTION);
    if (BekUtil.isBekEnabled()) {
      if (BekUtil.isLinearBekEnabled()) {
        group.replaceAction(intelliSortAction, new IntelliSortChooserPopupAction());
      }
    }
    else {
      // drop together with com.intellij.vcs.log.util.BekUtil.isBekEnabled
      group.remove(intelliSortAction);
    }
  }

  @Override
  public @Nullable Object getData(@NotNull @NonNls String dataId) {
    if (VcsDataKeys.CHANGES.is(dataId) || VcsDataKeys.SELECTED_CHANGES.is(dataId)) {
      return myChangesBrowser.getDirectChanges().toArray(Change.EMPTY_CHANGE_ARRAY);
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
      return myLogData.getLogProvider(Objects.requireNonNull(getFirstItem(roots))).getDiffHandler();
    }
    else if (VcsLogInternalDataKeys.VCS_LOG_VISIBLE_ROOTS.is(dataId)) {
      return VcsLogUtil.getAllVisibleRoots(myLogData.getRoots(), myFilterUi.getFilters());
    }
    else if (QuickActionProvider.KEY.is(dataId)) {
      return new QuickActionProvider() {
        @Override
        public @NotNull List<AnAction> getActions(boolean originalProvider) {
          AnAction textFilterAction = ActionUtil.wrap(VcsLogActionIds.VCS_LOG_FOCUS_TEXT_FILTER);
          textFilterAction.getTemplatePresentation().setText(VcsLogBundle.message("vcs.log.text.filter.action.text"));
          List<AnAction> actions = new ArrayList<>();
          actions.add(textFilterAction);
          actions.addAll(SimpleToolWindowPanel.collectActions(myToolbar));
          return actions;
        }

        @Override
        public JComponent getComponent() {
          return MainFrame.this;
        }

        @Override
        public @NlsActions.ActionText @Nullable String getName() {
          return null;
        }
      };
    }
    else if (PlatformCoreDataKeys.HELP_ID.is(dataId)) return HELP_ID;
    else if (History.KEY.is(dataId)) return myHistory;
    return null;
  }

  private @NotNull Collection<VirtualFile> getSelectedRoots() {
    Collection<VirtualFile> roots = myLogData.getRoots();
    if (roots.size() == 1) return roots;
    int[] selectedRows = myGraphTable.getSelectedRows();
    if (selectedRows.length == 0 || selectedRows.length > VcsLogUtil.MAX_SELECTED_COMMITS) {
      return VcsLogUtil.getAllVisibleRoots(roots, myFilterUi.getFilters());
    }
    return ContainerUtil.map2Set(Ints.asList(selectedRows), row -> myGraphTable.getModel().getRootAtRow(row));
  }

  public @NotNull JComponent getToolbar() {
    return myToolbar;
  }

  public @NotNull VcsLogChangesBrowser getChangesBrowser() {
    return myChangesBrowser;
  }

  public void showDetails(boolean state) {
    myDetailsSplitter.setSecondComponent(state ? myDetailsPanel : null);
  }

  public void selectFilePath(@NotNull FilePath filePath, boolean requestFocus) {
    if (myIsLoading) {
      myPathToSelect = filePath;
    }
    else {
      myChangesBrowser.selectFile(filePath);
      myPathToSelect = null;
    }

    if (requestFocus) {
      myChangesBrowser.getViewer().requestFocus();
    }
  }

  @Override
  public void dispose() {
    myDetailsSplitter.dispose();
    myChangesBrowserSplitter.dispose();
  }

  private class MyCommitSelectionListenerForDiff extends CommitSelectionListener<VcsFullCommitDetails> {
    private final @NotNull JBLoadingPanel myChangesLoadingPane;

    protected MyCommitSelectionListenerForDiff(@NotNull JBLoadingPanel changesLoadingPane) {
      super(MainFrame.this.myGraphTable, MainFrame.this.myLogData.getCommitDetailsGetter());
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
        String sizeText = VcsLogUtil.getSizeText(maxSize);
        myChangesBrowser.setEmptyWithText(statusText -> {
          statusText.setText(VcsLogBundle.message("vcs.log.changes.too.many.status", detailsList.size(), sizeText));
          statusText.appendSecondaryText(VcsLogBundle.message("vcs.log.changes.too.many.show.anyway.status.action"),
                                         SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                         e -> myChangesBrowser.setSelectedDetails(detailsList));
        });
      }
      else {
        myChangesBrowser.setSelectedDetails(detailsList);
      }
    }

    @Override
    protected int @NotNull [] onSelection(int @NotNull [] selection) {
      myChangesBrowser.setEmpty();
      return selection;
    }

    @Override
    protected void onLoadingScheduled() {
      myIsLoading = true;
      myPathToSelect = null;
    }

    @Override
    protected void onLoadingStarted() {
      myChangesLoadingPane.startLoading();
    }

    @Override
    protected void onLoadingStopped() {
      myChangesLoadingPane.stopLoading();
      myIsLoading = false;
      if (myPathToSelect != null) {
        myChangesBrowser.selectFile(myPathToSelect);
        myPathToSelect = null;
      }
    }

    @Override
    protected void onError(@NotNull Throwable error) {
      myChangesBrowser.setEmptyWithText(statusText -> statusText.setText(VcsLogBundle.message("vcs.log.error.loading.changes.status")));
    }
  }

  private class MyFocusPolicy extends ComponentsListFocusTraversalPolicy {
    @Override
    protected @NotNull List<Component> getOrderedComponents() {
      return List.of(myGraphTable,
                     myChangesBrowser.getPreferredFocusedComponent(),
                     myDiffPreview.getPreviewDiff().getPreferredFocusedComponent(),
                     myFilterUi.getTextFilterComponent().getFocusedComponent());
    }
  }

  private class MyVcsLogGraphTable extends VcsLogGraphTable {
    private final @NotNull Runnable myRefresh;

    MyVcsLogGraphTable(@NotNull String logId, @NotNull VcsLogData logData,
                       @NotNull VcsLogUiProperties uiProperties, @NotNull VcsLogColorManager colorManager,
                       @NotNull Runnable refresh, @NotNull Consumer<Runnable> requestMore,
                       @NotNull Disposable disposable) {
      super(logId, logData, uiProperties, colorManager, requestMore, disposable);
      myRefresh = refresh;
      IndexSpeedSearch speedSearch = new IndexSpeedSearch(myLogData.getProject(), myLogData.getIndex(), myLogData.getStorage(), this) {
        @Override
        protected boolean isSpeedSearchEnabled() {
          return Registry.is("vcs.log.speedsearch") && super.isSpeedSearchEnabled();
        }
      };
      speedSearch.setupListeners();
    }

    @Override
    protected void updateEmptyText() {
      StatusText statusText = getEmptyText();
      VisiblePack visiblePack = getModel().getVisiblePack();

      DataPackBase dataPack = visiblePack.getDataPack();
      if (dataPack instanceof DataPack.ErrorDataPack) {
        setErrorEmptyText(((DataPack.ErrorDataPack)dataPack).getError(),
                          VcsLogBundle.message("vcs.log.error.loading.commits.status"));
        appendActionToEmptyText(VcsLogBundle.message("vcs.log.refresh.status.action"),
                                () -> myLogData.refresh(myLogData.getLogProviders().keySet()));
      }
      else if (visiblePack instanceof VisiblePack.ErrorVisiblePack) {
        setErrorEmptyText(((VisiblePack.ErrorVisiblePack)visiblePack).getError(), VcsLogBundle.message("vcs.log.error.filtering.status"));
        if (visiblePack.getFilters().isEmpty()) {
          appendActionToEmptyText(VcsLogBundle.message("vcs.log.refresh.status.action"), myRefresh);
        }
        else {
          VcsLogUiUtil.appendResetFiltersActionToEmptyText(myFilterUi, getEmptyText());
        }
      }
      else if (visiblePack.getVisibleGraph().getVisibleCommitCount() == 0) {
        if (visiblePack.getFilters().isEmpty()) {
          statusText.setText(VcsLogBundle.message("vcs.log.no.commits.status")).
            appendSecondaryText(VcsLogBundle.message("vcs.log.commit.status.action"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                ActionUtil.createActionListener(IdeActions.ACTION_CHECKIN_PROJECT, this,
                                                                ActionPlaces.UNKNOWN));
          String shortcutText = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CHECKIN_PROJECT);
          if (!shortcutText.isEmpty()) {
            statusText.appendSecondaryText(" (" + shortcutText + ")", StatusText.DEFAULT_ATTRIBUTES, null);
          }
        }
        else {
          myFilterUi.setCustomEmptyText(getEmptyText());
        }
      }
      else {
        statusText.setText(VcsLogBundle.message("vcs.log.default.status"));
      }
    }
  }
}
