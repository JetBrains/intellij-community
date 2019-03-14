/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.history;

import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogContentUtil;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.actions.ShowPreviewEditorAction;
import com.intellij.vcs.log.ui.frame.DetailsPanel;
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
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class FileHistoryPanel extends JPanel implements DataProvider, Disposable {
  @NotNull private final VcsLogGraphTable myGraphTable;
  @NotNull private final DetailsPanel myDetailsPanel;
  @NotNull private final JBSplitter myDetailsSplitter;
  @Nullable private final FileHistoryDiffPreview myDiffPreview;
  @NotNull private final OnePixelSplitter myDiffPreviewSplitter;

  @NotNull private final FilePath myFilePath;
  @NotNull private final FileHistoryUi myUi;
  @NotNull private final VirtualFile myRoot;

  public FileHistoryPanel(@NotNull FileHistoryUi ui,
                          @NotNull VcsLogData logData,
                          @NotNull VisiblePack visiblePack,
                          @NotNull FilePath filePath) {
    myUi = ui;
    myFilePath = filePath;
    myRoot = notNull(VcsLogUtil.getActualRoot(logData.getProject(), myFilePath));
    myGraphTable = new VcsLogGraphTable(myUi, logData, visiblePack, myUi::requestMore) {
      @Override
      protected boolean isSpeedSearchEnabled() {
        return true;
      }

      @Override
      protected void updateEmptyText() {
        getEmptyText().setText("File history");
      }
    };
    myGraphTable.setCompactReferencesView(true);
    myGraphTable.setShowTagNames(false);

    myDetailsPanel = new DetailsPanel(logData, myUi.getColorManager(), this) {
      @Override
      protected void navigate(@NotNull CommitId commit) {
        VcsLogUiImpl mainLogUi = VcsProjectLog.getInstance(logData.getProject()).getMainLogUi();
        if (mainLogUi != null) {
          mainLogUi.jumpToCommit(commit.getHash(), commit.getRoot());
          VcsLogContentUtil.selectLogUi(logData.getProject(), mainLogUi);
        }
      }
    };
    myDetailsPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));

    myDetailsSplitter = new OnePixelSplitter(true, "vcs.log.history.details.splitter.proportion", 0.7f);
    JComponent tableWithProgress = VcsLogUiUtil.installProgress(VcsLogUiUtil.setupScrolledGraph(myGraphTable, SideBorder.LEFT),
                                                                logData, ui.getId(), this);
    myDetailsSplitter.setFirstComponent(tableWithProgress);
    myDetailsSplitter.setSecondComponent(myUi.getProperties().get(CommonUiProperties.SHOW_DETAILS) ? myDetailsPanel : null);

    myDetailsPanel.installCommitSelectionListener(myGraphTable);
    VcsLogUiUtil.installDetailsListeners(myGraphTable, myDetailsPanel, logData, this);

    JBPanel tablePanel = new JBPanel(new BorderLayout());
    tablePanel.add(myDetailsSplitter, BorderLayout.CENTER);
    tablePanel.add(createActionsToolbar(), BorderLayout.WEST);

    myDiffPreview = createDiffPreview();
    myDiffPreviewSplitter = new OnePixelSplitter(false, "vcs.history.diff.splitter.proportion", 0.7f);
    myDiffPreviewSplitter.setHonorComponentsMinimumSize(false);
    myDiffPreviewSplitter.setFirstComponent(tablePanel);
    ApplicationManager.getApplication().invokeLater(() -> showDiffPreview(myUi.getProperties().get(CommonUiProperties.SHOW_DIFF_PREVIEW)));

    setLayout(new BorderLayout());
    add(myDiffPreviewSplitter, BorderLayout.CENTER);

    PopupHandler.installPopupHandler(myGraphTable, VcsLogActionPlaces.HISTORY_POPUP_ACTION_GROUP, VcsLogActionPlaces.VCS_HISTORY_PLACE);
    invokeOnDoubleClick(ActionManager.getInstance().getAction(VcsLogActionPlaces.VCS_LOG_SHOW_DIFF_ACTION), tableWithProgress);

    Disposer.register(myUi, this);
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
    if (myDiffPreview != null) {
      myDiffPreview.updatePreview(myUi.getProperties().get(CommonUiProperties.SHOW_DIFF_PREVIEW));
    }
  }

  public void showDetails(boolean show) {
    myDetailsSplitter.setSecondComponent(show ? myDetailsPanel : null);
  }

  public boolean hasDiffPreview() {
    return myDiffPreview != null;
  }

  void showDiffPreview(boolean state) {
    if (myDiffPreview != null) {
      myDiffPreview.updatePreview(state);
      myDiffPreviewSplitter.setSecondComponent(state ? myDiffPreview.getComponent() : null);
    }
  }

  @Nullable
  private FileHistoryDiffPreview createDiffPreview() {
    if (!myFilePath.isDirectory()) {
      FileHistoryDiffPreview diffPreview = new FileHistoryDiffPreview(myUi.getLogData().getProject(), () -> myUi.getSelectedChange(), this);
      ListSelectionListener selectionListener = e -> {
        int[] selection = myGraphTable.getSelectedRows();
        ApplicationManager.getApplication().invokeLater(() -> diffPreview.updatePreview(diffPreview.getComponent().isShowing()),
                                                        o -> !Arrays.equals(selection, myGraphTable.getSelectedRows()));
      };
      myGraphTable.getSelectionModel().addListSelectionListener(selectionListener);
      Disposer.register(diffPreview, () -> myGraphTable.getSelectionModel().removeListSelectionListener(selectionListener));
      return diffPreview;
    }
    return null;
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (VcsDataKeys.CHANGES.is(dataId) || VcsDataKeys.SELECTED_CHANGES.is(dataId)) {
      Change change = myUi.getSelectedChange();
      if (change != null) {
        return new Change[]{change};
      }
      List<VcsFullCommitDetails> details = myUi.getVcsLog().getSelectedDetails();
      if (details.isEmpty() || details.size() > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
      return VcsLogUtil.collectChanges(details, detail -> myUi.collectRelevantChanges(detail)).toArray(new Change[0]);
    }
    else if (VcsLogInternalDataKeys.LOG_UI_PROPERTIES.is(dataId)) {
      return myUi.getProperties();
    }
    else if (VcsDataKeys.VCS_FILE_REVISION.is(dataId)) {
      List<VcsCommitMetadata> details = myUi.getVcsLog().getSelectedShortDetails();
      if (details.isEmpty()) return null;
      return myUi.createRevision(getFirstItem(details));
    }
    else if (VcsDataKeys.VCS_FILE_REVISIONS.is(dataId)) {
      List<VcsCommitMetadata> details = myUi.getVcsLog().getSelectedShortDetails();
      if (details.isEmpty() || details.size() > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
      return ContainerUtil.mapNotNull(details, myUi::createRevision).toArray(new VcsFileRevision[0]);
    }
    else if (VcsDataKeys.FILE_PATH.is(dataId)) {
      return myFilePath;
    }
    else if (VcsDataKeys.VCS_VIRTUAL_FILE.is(dataId)) {
      List<VcsCommitMetadata> details = myUi.getVcsLog().getSelectedShortDetails();
      if (details.isEmpty()) return null;
      VcsCommitMetadata detail = notNull(getFirstItem(details));
      Object revision = FileHistoryUtil.createVcsVirtualFile(myUi.createRevision(detail));
      if (revision != null) return revision;
    }
    else if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return myFilePath.getVirtualFile();
    }
    else if (VcsDataKeys.VCS_NON_LOCAL_HISTORY_SESSION.is(dataId)) {
      return false;
    }
    else if (VcsLogInternalDataKeys.LOG_DIFF_HANDLER.is(dataId)) {
      return myUi.getLogData().getLogProvider(myRoot).getDiffHandler();
    }
    else if (ShowPreviewEditorAction.DATA_KEY.is(dataId)) {
      if (myFilePath.isDirectory()) return null;
      return (Supplier<DiffRequestProcessor>)() -> {
        FileHistoryDiffPreview preview = notNull(createDiffPreview());
        preview.updatePreview(true);
        return preview;
      };
    }
    return null;
  }

  @Override
  public void dispose() {
    myDetailsSplitter.dispose();
  }
}
