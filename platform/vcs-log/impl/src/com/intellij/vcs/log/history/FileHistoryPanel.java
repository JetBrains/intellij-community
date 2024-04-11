// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history;

import com.intellij.diff.impl.DiffEditorViewer;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.ValueKey;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.navigation.History;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.vcs.log.UnsupportedHistoryFiltersException;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogContentUtil;
import com.intellij.vcs.log.impl.VcsLogNavigationUtil;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.*;
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel;
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel;
import com.intellij.vcs.log.ui.frame.ComponentQuickActionProvider;
import com.intellij.vcs.log.ui.frame.FrameDiffPreview;
import com.intellij.vcs.log.ui.frame.VcsLogCommitSelectionListenerForDetails;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import kotlin.Unit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

class FileHistoryPanel extends JPanel implements DataProvider, Disposable {
  private static final @NotNull @NonNls String HELP_ID = "reference.versionControl.toolwindow.history";

  private final @NotNull Project myProject;
  private final @NotNull FilePath myFilePath;
  private final @NotNull VirtualFile myRoot;

  private final @NotNull FileHistoryModel myFileHistoryModel;
  private final @NotNull VcsLogUiProperties myProperties;

  private final @NotNull VcsLogGraphTable myGraphTable;
  private final @NotNull FileHistorySpeedSearch mySpeedSearch;

  private final @NotNull CommitDetailsListPanel myDetailsPanel;
  private final @NotNull JBSplitter myDetailsSplitter;
  private final @NotNull JComponent myToolbar;

  private final @NotNull FrameDiffPreview myFrameDiffPreview;
  private final @NotNull FileHistoryEditorDiffPreview myEditorDiffPreview;

  private final @NotNull History myHistory;

  public FileHistoryPanel(@NotNull AbstractVcsLogUi logUi, @NotNull FileHistoryModel fileHistoryModel,
                          @NotNull FileHistoryFilterUi filterUi, @NotNull VcsLogData logData,
                          @NotNull FilePath filePath, @NotNull VirtualFile root,
                          @NotNull VcsLogColorManager colorManager,
                          @NotNull Disposable disposable) {
    myProject = logData.getProject();

    myFilePath = filePath;
    myRoot = root;

    myFileHistoryModel = fileHistoryModel;
    myProperties = logUi.getProperties();

    myGraphTable = new VcsLogGraphTable(logUi.getId(), logData, logUi.getProperties(), colorManager,
                                        () -> logUi.requestMore(EmptyRunnable.INSTANCE), disposable) {
      @Override
      protected void updateEmptyText() {
        VisiblePack visiblePack = getModel().getVisiblePack();
        if (visiblePack instanceof VisiblePack.ErrorVisiblePack errorVisiblePack) {
          Throwable error = errorVisiblePack.getError();
          setErrorEmptyText(error, VcsLogBundle.message("file.history.error.status"));
          if (error instanceof UnsupportedHistoryFiltersException) {
            appendActionToEmptyText(VcsLogBundle.message("file.history.reset.filters.status.action"),
                                    () -> filterUi.resetFiltersToDefault());
          }
          else {
            appendActionToEmptyText(VcsLogBundle.message("vcs.log.refresh.status.action"), () -> logUi.getRefresher().onRefresh());
          }
        }
        else {
          getEmptyText().setText(VcsLogBundle.message("file.history.empty.status"));
        }
      }
    };
    mySpeedSearch = new FileHistorySpeedSearch(myProject, logData.getIndex(), logData.getStorage(), myGraphTable);
    mySpeedSearch.setupListeners();

    myDetailsPanel = new CommitDetailsListPanel(myProject, this, () -> {
      return new CommitDetailsPanel(commit -> {
        VcsLogContentUtil.runInMainLog(myProject, ui -> {
          VcsLogNavigationUtil.jumpToCommit(ui, commit.getHash(), commit.getRoot(), false, true);
        });
        return Unit.INSTANCE;
      });
    });
    VcsLogCommitSelectionListenerForDetails.install(myGraphTable, myDetailsPanel, this,
                                                    VcsLogColorManagerFactory.create(Collections.singleton(myRoot)));

    myDetailsSplitter = new OnePixelSplitter(true, "vcs.log.history.details.splitter.proportion", 0.7f);
    JComponent tableWithProgress = VcsLogUiUtil.installScrollingAndProgress(myGraphTable, this);
    myDetailsSplitter.setFirstComponent(tableWithProgress);
    myDetailsSplitter.setSecondComponent(myProperties.get(CommonUiProperties.SHOW_DETAILS) ? myDetailsPanel : null);

    myEditorDiffPreview = new FileHistoryEditorDiffPreview(myProject, this);
    Disposer.register(this, myEditorDiffPreview);

    myToolbar = createActionsToolbar(filterUi);
    JBPanel tablePanel = new JBPanel(new BorderLayout()) {
      @Override
      public Dimension getMinimumSize() {
        return VcsLogUiUtil.expandToFitToolbar(super.getMinimumSize(), myToolbar);
      }
    };
    tablePanel.add(myDetailsSplitter, BorderLayout.CENTER);
    tablePanel.add(myToolbar, BorderLayout.NORTH);

    setLayout(new BorderLayout());
    myFrameDiffPreview = new FrameDiffPreview(myProperties, tablePanel, "vcs.history.diff.splitter.proportion",
                                                             0.7f, this) {
      @NotNull
      @Override
      protected DiffEditorViewer createViewer() {
        FileHistoryDiffProcessor processor = createDiffPreview(false);
        processor.setToolbarVerticalSizeReferent(myToolbar);
        return processor;
      }
    };
    add(myFrameDiffPreview.getMainComponent(), BorderLayout.CENTER);

    PopupHandler.installPopupMenu(myGraphTable, VcsLogActionIds.HISTORY_POPUP_ACTION_GROUP, ActionPlaces.VCS_HISTORY_PLACE);
    invokeOnDoubleClick(ActionManager.getInstance().getAction(VcsLogActionIds.VCS_LOG_SHOW_DIFF_ACTION), tableWithProgress);

    myHistory = VcsLogUiUtil.installNavigationHistory(logUi, myGraphTable);

    Disposer.register(disposable, this);

    myGraphTable.resetDefaultFocusTraversalKeys();
    setFocusCycleRoot(true);
    setFocusTraversalPolicy(new ComponentsListFocusTraversalPolicy() {
      @Override
      protected @NotNull List<Component> getOrderedComponents() {
        return ContainerUtil.skipNulls(Arrays.asList(myGraphTable, myFrameDiffPreview.getPreferredFocusedComponent(), myToolbar));
      }
    });
  }

  private void invokeOnDoubleClick(@NotNull AnAction action, @NotNull JComponent component) {
    new AnActionWrapper(action) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (e.getInputEvent() instanceof MouseEvent && myGraphTable.isResizingColumns()) {
          // disable action during columns resize
          return;
        }
        super.actionPerformed(e);
      }
    }.registerCustomShortcutSet(CommonShortcuts.DOUBLE_CLICK_1, component);
  }

  private @NotNull JComponent createActionsToolbar(@NotNull FileHistoryFilterUi filterUi) {
    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(filterUi.createActionGroup());
    toolbarGroup.addSeparator();
    AnAction toolbarActions = CustomActionsSchema.getInstance().getCorrectedAction(VcsLogActionIds.FILE_HISTORY_TOOLBAR_ACTION_GROUP);
    toolbarGroup.add(Objects.requireNonNull(toolbarActions));

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.VCS_HISTORY_TOOLBAR_PLACE,
                                                                            toolbarGroup, true);
    toolbar.setTargetComponent(myGraphTable);

    ActionGroup rightToolbarGroup = new DefaultActionGroup(ActionManager.getInstance().getAction(VcsLogActionIds.FILE_HISTORY_TOOLBAR_RIGHT_CORNER_ACTION_GROUP));
    ActionToolbar rightCornerToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.VCS_HISTORY_TOOLBAR_PLACE,
                                                                                       rightToolbarGroup, true);
    rightCornerToolbar.setTargetComponent(myGraphTable);

    BorderLayoutPanel panel = new BorderLayoutPanel();
    GuiUtils.installVisibilityReferent(panel, toolbar.getComponent());
    panel.addToCenter(toolbar.getComponent());
    panel.addToRight(rightCornerToolbar.getComponent());
    return panel;
  }

  public @NotNull VcsLogGraphTable getGraphTable() {
    return myGraphTable;
  }

  public @NotNull JComponent getToolbar() {
    return myToolbar;
  }

  public void updateDataPack(@NotNull VisiblePack visiblePack, boolean permanentGraphChanged) {
    myGraphTable.updateDataPack(visiblePack, permanentGraphChanged);
    mySpeedSearch.setVisiblePack(visiblePack);
  }

  public void showDetails(boolean show) {
    myDetailsSplitter.setSecondComponent(show ? myDetailsPanel : null);
  }

  @NotNull
  FileHistoryDiffProcessor createDiffPreview(boolean isInEditor) {
    FileHistoryDiffProcessor diffPreview = new FileHistoryDiffProcessor(myProject, () -> getSelectedChange(), isInEditor, this);
    ListSelectionListener selectionListener = e -> {
      int[] selection = myGraphTable.getSelectedRows();
      ApplicationManager.getApplication().invokeLater(() -> diffPreview.updatePreview(),
                                                      o -> !Arrays.equals(selection, myGraphTable.getSelectedRows()) ||
                                                           Disposer.isDisposed(diffPreview));
    };
    myGraphTable.getSelectionModel().addListSelectionListener(selectionListener);
    Disposer.register(diffPreview, () -> myGraphTable.getSelectionModel().removeListSelectionListener(selectionListener));

    TableModelListener modelListener = e -> {
      if (e.getColumn() < 0) {
        ApplicationManager.getApplication().invokeLater(() -> diffPreview.updatePreview(),
                                                        o -> Disposer.isDisposed(diffPreview));
      }
    };
    UiNotifyConnector.installOn(diffPreview.getComponent(), new Activatable() {
      @Override
      public void showNotify() {
        diffPreview.updatePreview();
      }
    });

    myGraphTable.getModel().addTableModelListener(modelListener);
    Disposer.register(diffPreview, () -> myGraphTable.getModel().removeTableModelListener(modelListener));

    return diffPreview;
  }

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    return ValueKey.match(dataId)
      .ifEq(VcsDataKeys.CHANGES).or(VcsDataKeys.SELECTED_CHANGES).thenGet(() -> {
        Change change = getSelectedChange();
        if (change != null) {
          return new Change[]{change};
        }
        return null;
      })
      .ifEq(VcsLogInternalDataKeys.LOG_UI_PROPERTIES).then(myProperties)
      .ifEq(VcsDataKeys.FILE_PATH).then(myFilePath)
      .ifEq(VcsLogInternalDataKeys.VCS_LOG_VISIBLE_ROOTS).thenGet(() -> Collections.singleton(myRoot))
      .ifEq(VcsDataKeys.VCS_NON_LOCAL_HISTORY_SESSION).then(false)
      .ifEq(VcsLogInternalDataKeys.LOG_DIFF_HANDLER).thenGet(() -> myFileHistoryModel.getDiffHandler())
      .ifEq(EditorTabDiffPreviewManager.EDITOR_TAB_DIFF_PREVIEW).thenGet(() -> myEditorDiffPreview)
      .ifEq(VcsLogInternalDataKeys.FILE_HISTORY_MODEL).thenGet(() -> myFileHistoryModel.createSnapshot())
      .ifEq(QuickActionProvider.KEY).thenGet(() -> new ComponentQuickActionProvider(this))
      .ifEq(PlatformCoreDataKeys.BGT_DATA_PROVIDER).thenGet(() -> {
        List<VcsCommitMetadata> details = myGraphTable.getSelection().getCachedMetadata();
        FileHistoryModel modelSnapshot = myFileHistoryModel.createSnapshot();
        return (slowId) -> getSlowData(slowId, modelSnapshot, details);
      })
      .ifEq(PlatformCoreDataKeys.HELP_ID).then(HELP_ID)
      .ifEq(History.KEY).then(myHistory)
      .orNull();
  }

  private @Nullable Object getSlowData(@NotNull String dataId, @NotNull FileHistoryModel model, @NotNull List<VcsCommitMetadata> details) {
    return ValueKey.match(dataId)
      .ifEq(VcsDataKeys.VCS_FILE_REVISION).thenGet(() -> {
        if (details.isEmpty()) return null;
        return model.createRevision(getFirstItem(details));
      })
      .ifEq(VcsDataKeys.VCS_FILE_REVISIONS).thenGet(() -> {
        if (details.isEmpty() || details.size() > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
        return ContainerUtil.mapNotNull(details, model::createRevision).toArray(new VcsFileRevision[0]);
      })
      .ifEq(CommonDataKeys.VIRTUAL_FILE).thenGet(myFilePath::getVirtualFile)
      .ifEq(VcsDataKeys.VCS_VIRTUAL_FILE).thenGet(() -> {
        if (details.isEmpty()) return null;
        VcsCommitMetadata detail = Objects.requireNonNull(getFirstItem(details));
        return FileHistoryUtil.createVcsVirtualFile(model.createRevision(detail));
      })
      .orNull();
  }

  @Nullable
  Change getSelectedChange() {
    return myFileHistoryModel.getSelectedChange(myGraphTable.getSelectedRows());
  }

  @NotNull
  FilePath getFilePath() {
    return myFilePath;
  }

  @Override
  public void dispose() {
    myDetailsSplitter.dispose();
  }
}
