// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history;

import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ValueKey;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.DiffPreviewProvider;
import com.intellij.openapi.vcs.changes.PreviewDiffVirtualFile;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogContentUtil;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.frame.VcsLogCommitDetailsListPanel;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class FileHistoryPanel extends JPanel implements DataProvider, Disposable {
  @NotNull private final VcsLogGraphTable myGraphTable;
  @NotNull private final VcsLogCommitDetailsListPanel myDetailsPanel;
  @NotNull private final JBSplitter myDetailsSplitter;
  @NotNull private final FileHistoryDiffPreview myDiffPreview;
  @NotNull private final OnePixelSplitter myDiffPreviewSplitter;

  @NotNull private final VcsLogData myLogData;
  @NotNull private final FilePath myFilePath;
  @NotNull private final VirtualFile myRoot;
  @NotNull private final FileHistoryUi myUi;
  @NotNull private final FileHistoryUiProperties myProperties;
  @NotNull private DiffPreviewProvider myDiffPreviewProvider;

  public FileHistoryPanel(@NotNull FileHistoryUi ui,
                          @NotNull VcsLogData logData,
                          @NotNull FilePath filePath) {
    myLogData = logData;

    myFilePath = filePath;
    myRoot = notNull(VcsLogUtil.getActualRoot(myLogData.getProject(), myFilePath));

    myUi = ui;
    myProperties = ui.getProperties();

    myGraphTable = new VcsLogGraphTable(myUi, myLogData, myUi::requestMore) {
      @Override
      protected boolean isSpeedSearchEnabled() {
        return true;
      }

      @Override
      protected void updateEmptyText() {
        VisiblePack visiblePack = getModel().getVisiblePack();
        if (visiblePack instanceof VisiblePack.ErrorVisiblePack) {
          setErrorEmptyText(((VisiblePack.ErrorVisiblePack)visiblePack).getError(), "Error calculating file history");
          appendActionToEmptyText("Refresh", () -> myUi.getRefresher().onRefresh());
        }
        else {
          getEmptyText().setText("File history");
        }
      }
    };
    myGraphTable.setCompactReferencesView(true);
    myGraphTable.setShowTagNames(false);
    myGraphTable.setLabelsLeftAligned(false);
    myGraphTable.setBorder(myGraphTable.createTopBottomBorder(1, 0));

    myDetailsPanel = new VcsLogCommitDetailsListPanel(myLogData, new VcsLogColorManagerImpl(Collections.singleton(myRoot)), this) {
      @Override
      protected void navigate(@NotNull CommitId commit) {
        VcsLogContentUtil.openMainLogAndExecute(myLogData.getProject(), ui -> {
          ui.jumpToCommit(commit.getHash(), commit.getRoot());
        });
      }
    };
    myDetailsPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));

    myDetailsSplitter = new OnePixelSplitter(true, "vcs.log.history.details.splitter.proportion", 0.7f);
    JComponent tableWithProgress = VcsLogUiUtil.installProgress(VcsLogUiUtil.setupScrolledGraph(myGraphTable, SideBorder.LEFT),
                                                                myLogData, ui.getId(), this);
    myDetailsSplitter.setFirstComponent(tableWithProgress);
    myDetailsSplitter.setSecondComponent(myProperties.get(CommonUiProperties.SHOW_DETAILS) ? myDetailsPanel : null);

    myDetailsPanel.installCommitSelectionListener(myGraphTable);
    VcsLogUiUtil.installDetailsListeners(myGraphTable, myDetailsPanel, myLogData, this);

    JBPanel tablePanel = new JBPanel(new BorderLayout());
    tablePanel.add(myDetailsSplitter, BorderLayout.CENTER);
    tablePanel.add(createActionsToolbar(), BorderLayout.WEST);

    myDiffPreview = createDiffPreview(false);
    myDiffPreviewSplitter = new OnePixelSplitter(false, "vcs.history.diff.splitter.proportion", 0.7f);
    myDiffPreviewSplitter.setHonorComponentsMinimumSize(false);
    myDiffPreviewSplitter.setFirstComponent(tablePanel);
    ApplicationManager.getApplication().invokeLater(() -> showDiffPreview(myProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW)));

    setLayout(new BorderLayout());
    add(myDiffPreviewSplitter, BorderLayout.CENTER);

    PopupHandler.installPopupHandler(myGraphTable, VcsLogActionPlaces.HISTORY_POPUP_ACTION_GROUP, VcsLogActionPlaces.VCS_HISTORY_PLACE);
    invokeOnDoubleClick(ActionManager.getInstance().getAction(VcsLogActionPlaces.VCS_LOG_SHOW_DIFF_ACTION), tableWithProgress);

    installEditorPreview();

    Disposer.register(myUi, this);
  }

  private void installEditorPreview() {
    myDiffPreviewProvider = new DiffPreviewProvider() {
      @NotNull
      @Override
      public DiffRequestProcessor createDiffRequestProcessor() {
        FileHistoryDiffPreview preview = notNull(createDiffPreview(true));
        preview.updatePreview(true);
        return preview;
      }

      @NotNull
      @Override
      public Object getOwner() {
        return myUi;
      }

      @Override
      public String getEditorTabName() {
        return String.format("History for '%s'", myFilePath.getName());
      }
    };

    ListSelectionListener selectionListener = e -> {
      if (Registry.is("show.diff.preview.as.editor.tab") &&
          myProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW) && !myGraphTable.getSelectionModel().isSelectionEmpty()) {
        FileEditorManager instance = FileEditorManager.getInstance(myLogData.getProject());
        PreviewDiffVirtualFile file = new PreviewDiffVirtualFile(myDiffPreviewProvider);
        ApplicationManager.getApplication().invokeLater(() -> {
          instance.openFile(file, false, true);
        }, ModalityState.NON_MODAL);
      }
    };

    myGraphTable.getSelectionModel().addListSelectionListener(selectionListener);
    Disposer.register(this, () -> myGraphTable.getSelectionModel().removeListSelectionListener(selectionListener));
  }

  private void invokeOnDoubleClick(@NotNull AnAction action, @NotNull JComponent component) {
    new EmptyAction.MyDelegatingAction(action) {
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

  @NotNull
  private JComponent createActionsToolbar() {
    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(ActionManager.getInstance().getAction(VcsLogActionPlaces.FILE_HISTORY_TOOLBAR_ACTION_GROUP));

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(VcsLogActionPlaces.VCS_HISTORY_TOOLBAR_PLACE,
                                                                            toolbarGroup, false);
    toolbar.setTargetComponent(myGraphTable);
    return toolbar.getComponent();
  }

  @NotNull
  public VcsLogGraphTable getGraphTable() {
    return myGraphTable;
  }

  public void updateDataPack(@NotNull VisiblePack visiblePack, boolean permanentGraphChanged) {
    myGraphTable.updateDataPack(visiblePack, permanentGraphChanged);
    myDiffPreview.updatePreview(myProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW));
  }

  public void showDetails(boolean show) {
    myDetailsSplitter.setSecondComponent(show ? myDetailsPanel : null);
  }

  void showDiffPreview(boolean state) {
    if (Registry.is("show.diff.preview.as.editor.tab")) {
      if (!state) {
        FileEditorManager.getInstance(myLogData.getProject()).closeFile(new PreviewDiffVirtualFile(myDiffPreviewProvider));
      }
      else {
        FileEditorManager.getInstance(myLogData.getProject()).openFile(new PreviewDiffVirtualFile(myDiffPreviewProvider), false, true);
      }
      return;
    }

    myDiffPreview.updatePreview(state);
    myDiffPreviewSplitter.setSecondComponent(state ? myDiffPreview.getComponent() : null);
  }

  @NotNull
  private FileHistoryDiffPreview createDiffPreview(boolean isInEditor) {
    FileHistoryDiffPreview diffPreview = new FileHistoryDiffPreview(myLogData.getProject(), () -> myUi.getSelectedChange(),
                                                                    isInEditor, this);
    ListSelectionListener selectionListener = e -> {
      if (!myProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW)) {
        return;
      }
      int[] selection = myGraphTable.getSelectedRows();
      ApplicationManager.getApplication().invokeLater(() -> diffPreview.updatePreview(diffPreview.getComponent().isShowing()),
                                                      o -> !Arrays.equals(selection, myGraphTable.getSelectedRows()));
    };
    myGraphTable.getSelectionModel().addListSelectionListener(selectionListener);
    Disposer.register(diffPreview, () -> myGraphTable.getSelectionModel().removeListSelectionListener(selectionListener));
    return diffPreview;
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    return ValueKey.match(dataId)
      .ifEq(VcsDataKeys.CHANGES).or(VcsDataKeys.SELECTED_CHANGES).thenGet(() -> {
        Change change = myUi.getSelectedChange();
        if (change != null) {
          return new Change[]{change};
        }
        return null;
      })
      .ifEq(VcsLogInternalDataKeys.LOG_UI_PROPERTIES).then(myProperties)
      .ifEq(VcsDataKeys.VCS_FILE_REVISION).thenGet(() -> {
        List<VcsCommitMetadata> details = getSelectedMetadata();
        if (details.isEmpty()) return null;
        return myUi.createRevision(getFirstItem(details));
      })
      .ifEq(VcsDataKeys.VCS_FILE_REVISIONS).thenGet(() -> {
        List<VcsCommitMetadata> details = getSelectedMetadata();
        if (details.isEmpty() || details.size() > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
        return ContainerUtil.mapNotNull(details, myUi::createRevision).toArray(new VcsFileRevision[0]);
      })
      .ifEq(VcsDataKeys.FILE_PATH).then(myFilePath)
      .ifEq(VcsDataKeys.VCS_VIRTUAL_FILE).thenGet(() -> {
        List<VcsCommitMetadata> details = getSelectedMetadata();
        if (details.isEmpty()) return null;
        VcsCommitMetadata detail = notNull(getFirstItem(details));
        return FileHistoryUtil.createVcsVirtualFile(myUi.createRevision(detail));
      })
      .ifEq(CommonDataKeys.VIRTUAL_FILE).thenGet(myFilePath::getVirtualFile)
      .ifEq(VcsDataKeys.VCS_NON_LOCAL_HISTORY_SESSION).then(false)
      .ifEq(VcsLogInternalDataKeys.LOG_DIFF_HANDLER).thenGet(() -> myLogData.getLogProvider(myRoot).getDiffHandler())
      .orNull();
  }

  @NotNull
  private List<VcsCommitMetadata> getSelectedMetadata() {
    return myGraphTable.getModel().getCommitMetadata(myGraphTable.getSelectedRows());
  }

  @Override
  public void dispose() {
    myDetailsSplitter.dispose();
  }
}
