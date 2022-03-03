// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.google.common.primitives.Ints;
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
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.util.Consumer;
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

import static com.intellij.openapi.vfs.VfsUtilCore.toVirtualFileArray;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class MainFrame extends JPanel implements DataProvider, Disposable {
  @NonNls private static final String DIFF_SPLITTER_PROPORTION = "vcs.log.diff.splitter.proportion";
  @NonNls private static final String DETAILS_SPLITTER_PROPORTION = "vcs.log.details.splitter.proportion";
  @NonNls private static final String CHANGES_SPLITTER_PROPORTION = "vcs.log.changes.splitter.proportion";

  @NotNull private final VcsLogData myLogData;
  @NotNull private final MainVcsLogUiProperties myUiProperties;

  @NotNull private final JComponent myToolbar;
  @NotNull private final VcsLogGraphTable myGraphTable;

  @NotNull private final VcsLogFilterUiEx myFilterUi;

  @NotNull private final VcsLogChangesBrowser myChangesBrowser;
  @NotNull private final Splitter myChangesBrowserSplitter;

  @NotNull private final CommitDetailsListPanel myDetailsPanel;
  @NotNull private final Splitter myDetailsSplitter;
  @NotNull private final EditorNotificationPanel myNotificationLabel;

  private boolean myIsLoading;
  @Nullable private FilePath myPathToSelect = null;

  @NotNull private final FrameDiffPreview<VcsLogChangeProcessor> myDiffPreview;

  public MainFrame(@NotNull VcsLogData logData,
                   @NotNull AbstractVcsLogUi logUi,
                   @NotNull MainVcsLogUiProperties uiProperties,
                   @NotNull VcsLogFilterUiEx filterUi,
                   boolean withEditorDiffPreview,
                   @NotNull Disposable disposable) {
    myLogData = logData;
    myUiProperties = uiProperties;

    myFilterUi = filterUi;

    myGraphTable = new MyVcsLogGraphTable(logUi.getId(), logData, logUi.getProperties(), logUi.getColorManager(),
                                          () -> logUi.getRefresher().onRefresh(), logUi::requestMore, disposable);
    String vcsDisplayName = VcsLogUtil.getVcsDisplayName(logData.getProject(), logData.getLogProviders().values());
    myGraphTable.getAccessibleContext().setAccessibleName(VcsLogBundle.message("vcs.log.table.accessible.name", vcsDisplayName));

    PopupHandler.installPopupMenu(myGraphTable, VcsLogActionIds.POPUP_ACTION_GROUP, ActionPlaces.VCS_LOG_TABLE_PLACE);

    myDetailsPanel = new CommitDetailsListPanel(logData.getProject(), this, () -> {
      return new CommitDetailsPanel(commit -> {
        logUi.getVcsLog().jumpToCommit(commit.getHash(), commit.getRoot());
        return Unit.INSTANCE;
      });
    });
    VcsLogCommitSelectionListenerForDetails.install(myGraphTable, myDetailsPanel, this);

    myChangesBrowser = new VcsLogChangesBrowser(logData.getProject(), myUiProperties, (commitId) -> {
      int index = myLogData.getCommitIndex(commitId.getHash(), commitId.getRoot());
      return myLogData.getMiniDetailsGetter().getCommitData(index);
    }, withEditorDiffPreview, this);
    myChangesBrowser.getAccessibleContext().setAccessibleName(VcsLogBundle.message("vcs.log.changes.accessible.name"));
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

    myNotificationLabel = new EditorNotificationPanel(UIUtil.getPanelBackground());
    myNotificationLabel.setVisible(false);
    myNotificationLabel.setBorder(new CompoundBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                                                     notNull(myNotificationLabel.getBorder(), JBUI.Borders.empty())));

    JComponent toolbars = new JPanel(new BorderLayout());
    toolbars.add(myToolbar, BorderLayout.NORTH);
    toolbars.add(myNotificationLabel, BorderLayout.CENTER);
    JComponent toolbarsAndTable = new JPanel(new BorderLayout());
    toolbarsAndTable.add(toolbars, BorderLayout.NORTH);
    toolbarsAndTable.add(VcsLogUiUtil.installProgress(VcsLogUiUtil.setupScrolledGraph(myGraphTable, SideBorder.TOP),
                                                      myLogData, logUi.getId(), this), BorderLayout.CENTER);

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

    Disposer.register(disposable, this);
    myGraphTable.resetDefaultFocusTraversalKeys();
    setFocusCycleRoot(true);
    setFocusTraversalPolicy(new MyFocusPolicy());
  }

  public void setExplanationHtml(@Nullable @NlsContexts.LinkLabel String text) {
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

  @NotNull
  private JComponent createActionsToolbar() {
    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.copyFromGroup((DefaultActionGroup)ActionManager.getInstance().getAction(VcsLogActionIds.TOOLBAR_ACTION_GROUP));
    if (BekUtil.isBekEnabled()) {
      Constraints constraint = new Constraints(Anchor.BEFORE, VcsLogActionIds.PRESENTATION_SETTINGS_ACTION_GROUP);
      if (BekUtil.isLinearBekEnabled()) {
        toolbarGroup.add(new IntelliSortChooserPopupAction(), constraint);
        // can not register both of the actions in xml file, choosing to register an action for the "outer world"
        // I can of course if linear bek is enabled replace the action on start but why bother
      }
      else {
        toolbarGroup.add(ActionManager.getInstance().getAction(VcsLogActionIds.VCS_LOG_INTELLI_SORT_ACTION),
                         constraint);
      }
    }

    DefaultActionGroup mainGroup = new DefaultActionGroup();
    mainGroup.add(ActionManager.getInstance().getAction(VcsLogActionIds.TEXT_FILTER_SETTINGS_ACTION_GROUP));
    mainGroup.add(new Separator());
    mainGroup.add(myFilterUi.createActionGroup());
    mainGroup.addSeparator();
    mainGroup.add(toolbarGroup);
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.VCS_LOG_TOOLBAR_PLACE, mainGroup, true);
    toolbar.setTargetComponent(this);

    Wrapper textFilter = new Wrapper(myFilterUi.getTextFilterComponent());
    textFilter.setVerticalSizeReferent(toolbar.getComponent());
    String vcsDisplayName = VcsLogUtil.getVcsDisplayName(myLogData.getProject(), myLogData.getLogProviders().values());
    textFilter.getAccessibleContext().setAccessibleName(VcsLogBundle.message("vcs.log.text.filter.accessible.name", vcsDisplayName));

    DefaultActionGroup rightCornerGroup =
      new DefaultActionGroup(ActionManager.getInstance().getAction(VcsLogActionIds.TOOLBAR_RIGHT_CORNER_ACTION_GROUP));
    ActionToolbar rightCornerToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.VCS_LOG_TOOLBAR_PLACE,
                                                                                       rightCornerGroup, true);
    rightCornerToolbar.setTargetComponent(this);
    rightCornerToolbar.setReservePlaceAutoPopupIcon(false);
    rightCornerToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);

    JPanel panel = new JPanel(new MigLayout("ins 0, fill", "[left]0[left, fill]push[pref:pref, right]", "center"));
    GuiUtils.installVisibilityReferent(panel, toolbar.getComponent());
    panel.add(textFilter);
    panel.add(toolbar.getComponent());
    panel.add(rightCornerToolbar.getComponent());
    return panel;
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
      return myLogData.getLogProvider(Objects.requireNonNull(getFirstItem(roots))).getDiffHandler();
    }
    else if (VcsLogInternalDataKeys.VCS_LOG_VISIBLE_ROOTS.is(dataId)) {
      return VcsLogUtil.getAllVisibleRoots(myLogData.getRoots(), myFilterUi.getFilters());
    }
    else if (QuickActionProvider.KEY.is(dataId)) {
      return new QuickActionProvider() {
        @Override
        public @NotNull List<AnAction> getActions(boolean originalProvider) {
          AnAction textFilterAction = EmptyAction.wrap(ActionManager.getInstance().getAction(VcsLogActionIds.VCS_LOG_FOCUS_TEXT_FILTER));
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

  @NotNull
  public VcsLogChangesBrowser getChangesBrowser() {
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
      myChangesBrowser.getViewer().selectFile(filePath);
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
    @NotNull private final JBLoadingPanel myChangesLoadingPane;

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
        myChangesBrowser.showText(statusText -> {
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
      myChangesBrowser.resetSelectedDetails();
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
        myChangesBrowser.getViewer().selectFile(myPathToSelect);
        myPathToSelect = null;
      }
    }

    @Override
    protected void onError(@NotNull Throwable error) {
      myChangesBrowser.showText(statusText -> statusText.setText(VcsLogBundle.message("vcs.log.error.loading.status")));
    }
  }

  private class MyFocusPolicy extends ComponentsListFocusTraversalPolicy {
    @NotNull
    @Override
    protected List<Component> getOrderedComponents() {
      return ContainerUtil.newArrayList(myGraphTable, myChangesBrowser.getPreferredFocusedComponent(),
                                        myDiffPreview.getPreviewDiff().getPreferredFocusedComponent(),
                                        myFilterUi.getTextFilterComponent().getTextEditor());
    }
  }

  private class MyVcsLogGraphTable extends VcsLogGraphTable {
    @NotNull private final Runnable myRefresh;

    MyVcsLogGraphTable(@NotNull String logId, @NotNull VcsLogData logData,
                       @NotNull VcsLogUiProperties uiProperties, @NotNull VcsLogColorManager colorManager,
                       @NotNull Runnable refresh, @NotNull Consumer<Runnable> requestMore,
                       @NotNull Disposable disposable) {
      super(logId, logData, uiProperties, colorManager, requestMore, disposable);
      myRefresh = refresh;
      new IndexSpeedSearch(myLogData.getProject(), myLogData.getIndex(), myLogData.getStorage(), this) {
        @Override
        protected boolean isSpeedSearchEnabled() {
          return Registry.is("vcs.log.speedsearch") && super.isSpeedSearchEnabled();
        }
      };
    }

    @Override
    protected void updateEmptyText() {
      StatusText statusText = getEmptyText();
      VisiblePack visiblePack = getModel().getVisiblePack();

      DataPackBase dataPack = visiblePack.getDataPack();
      if (dataPack instanceof DataPack.ErrorDataPack) {
        setErrorEmptyText(((DataPack.ErrorDataPack)dataPack).getError(),
                          VcsLogBundle.message("vcs.log.error.loading.status"));
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
