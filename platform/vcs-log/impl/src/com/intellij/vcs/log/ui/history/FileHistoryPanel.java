/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.history;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.ui.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.index.IndexDataGetter;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.frame.CommitSelectionListenerForDiff;
import com.intellij.vcs.log.ui.frame.DetailsPanel;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class FileHistoryPanel extends JPanel implements DataProvider, Disposable {
  private static final Logger LOG = Logger.getInstance(FileHistoryPanel.class);
  @NotNull private final VcsLogGraphTable myGraphTable;
  @NotNull private final DetailsPanel myDetailsPanel;
  @NotNull private final JBSplitter myDetailsSplitter;
  @NotNull private final VcsLogData myLogData;
  @NotNull private final FilePath myFilePath;
  @NotNull private final FileHistoryUi myUi;

  @NotNull private List<Change> mySelectedChanges = Collections.emptyList();
  @NotNull private IndexDataGetter myIndexDataGetter;

  public FileHistoryPanel(@NotNull FileHistoryUi ui,
                          @NotNull VcsLogData logData,
                          @NotNull VisiblePack visiblePack,
                          @NotNull FilePath filePath) {
    myUi = ui;
    myLogData = logData;
    myIndexDataGetter = ObjectUtils.assertNotNull(logData.getIndex().getDataGetter());
    myFilePath = filePath;
    myGraphTable = new VcsLogGraphTable(myUi, logData, visiblePack) {
      @Override
      protected boolean isSpeedSearchEnabled() {
        return true;
      }
    };
    myGraphTable.setCompactReferencesView(true);
    myGraphTable.setShowTagNames(false);

    myDetailsPanel = new DetailsPanel(logData, myUi.getColorManager(), this);
    myDetailsPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));

    myDetailsSplitter = new OnePixelSplitter(true, "vcs.log.history.details.splitter.proportion", 0.7f);
    myDetailsSplitter.setFirstComponent(VcsLogUiUtil.installProgress(VcsLogUiUtil.setupScrolledGraph(myGraphTable, SideBorder.LEFT),
                                                                     myLogData, this));
    myDetailsSplitter.setSecondComponent(myUi.getProperties().get(MainVcsLogUiProperties.SHOW_DETAILS) ? myDetailsPanel : null);

    myGraphTable.getSelectionModel().addListSelectionListener(new MyCommitSelectionListenerForDiff());
    myDetailsPanel.installCommitSelectionListener(myGraphTable);
    VcsLogUiUtil.installDetailsListeners(myGraphTable, myDetailsPanel, myLogData, this);

    setLayout(new BorderLayout());
    add(myDetailsSplitter, BorderLayout.CENTER);
    add(createActionsToolbar(), BorderLayout.WEST);

    PopupHandler.installPopupHandler(myGraphTable, VcsLogActionPlaces.HISTORY_POPUP_ACTION_GROUP, VcsLogActionPlaces.VCS_HISTORY_PLACE);
    EmptyAction.wrap(ActionManager.getInstance().getAction(VcsLogActionPlaces.VCS_LOG_SHOW_DIFF_ACTION)).
      registerCustomShortcutSet(CommonShortcuts.DOUBLE_CLICK_1, myGraphTable);

    Disposer.register(myUi, this);
  }

  @NotNull
  private JComponent createActionsToolbar() {
    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(ActionManager.getInstance().getAction(VcsLogActionPlaces.FILE_HISTORY_TOOLBAR_ACTION_GROUP));

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CHANGES_VIEW_TOOLBAR, toolbarGroup, false);
    toolbar.setTargetComponent(myGraphTable);
    return toolbar.getComponent();
  }

  @NotNull
  public VcsLogGraphTable getGraphTable() {
    return myGraphTable;
  }

  public void updateDataPack(@NotNull VisiblePack visiblePack, boolean permanentGraphChanged) {
    myGraphTable.updateDataPack(visiblePack, permanentGraphChanged);
  }

  public void showDetails(boolean show) {
    myDetailsSplitter.setSecondComponent(show ? myDetailsPanel : null);
  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    if (VcsDataKeys.CHANGES.is(dataId) || VcsDataKeys.SELECTED_CHANGES.is(dataId)) {
      if (myGraphTable.getSelectedRowCount() == 0) return null;
      return ArrayUtil.toObjectArray(mySelectedChanges, Change.class);
    }
    else if (VcsLogInternalDataKeys.LOG_UI_PROPERTIES.is(dataId)) {
      return myUi.getProperties();
    }
    else if (VcsDataKeys.VCS_FILE_REVISION.is(dataId)) {
      List<VcsFullCommitDetails> details = myUi.getVcsLog().getSelectedDetails();
      if (details.isEmpty()) return null;
      return myUi.createRevision(getFirstItem(details));
    }
    else if (VcsDataKeys.VCS_FILE_REVISIONS.is(dataId)) {
      List<VcsFullCommitDetails> details = myUi.getVcsLog().getSelectedDetails();
      if (details.size() > VcsLogUtil.MAX_SELECTED_COMMITS) return null;
      return ArrayUtil.toObjectArray(ContainerUtil.mapNotNull(details, myUi::createRevision), VcsFileRevision.class);
    }
    else if (VcsDataKeys.FILE_PATH.is(dataId)) {
      return myFilePath;
    }
    else if (VcsDataKeys.VCS_VIRTUAL_FILE.is(dataId)) {
      VcsFullCommitDetails details = notNull(getFirstItem(myUi.getVcsLog().getSelectedDetails()));
      Object revision = myUi.createVcsVirtualFile(details);
      if (revision != null) return revision;
    }
    else if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return myFilePath.getVirtualFile();
    }
    else if (VcsDataKeys.VCS_NON_LOCAL_HISTORY_SESSION.is(dataId)) {
      return false;
    }
    return null;
  }

  @Override
  public void dispose() {
    myDetailsSplitter.dispose();
  }

  private class MyCommitSelectionListenerForDiff extends CommitSelectionListenerForDiff {

    protected MyCommitSelectionListenerForDiff() {
      super(myLogData, FileHistoryPanel.this.myGraphTable);
    }

    @Override
    protected void onDetailsLoaded(@NotNull List<VcsFullCommitDetails> detailsList) {
      List<Change> changes = ContainerUtil.newArrayList();
      List<VcsFullCommitDetails> detailsListReversed = ContainerUtil.reverse(detailsList);
      for (VcsFullCommitDetails details : detailsListReversed) {
        changes.addAll(myUi.collectRelevantChanges(details));
      }
      changes = CommittedChangesTreeBrowser.zipChanges(changes);
      setChangesToDisplay(changes);
    }

    @Override
    protected void setChangesToDisplay(@NotNull List<Change> changes) {
      mySelectedChanges = changes;
    }

    @Override
    protected void clearChanges() {
      mySelectedChanges = Collections.emptyList();
    }

    @Override
    protected void startLoading() {
    }

    @Override
    protected void stopLoading() {
    }
  }
}
