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
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogProgress;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogActionPlaces;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.frame.DetailsPanel;
import com.intellij.vcs.log.ui.frame.ProgressStripe;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.visible.VisiblePack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class FileHistoryPanel extends JPanel implements DataProvider, Disposable {
  @NotNull private final VcsLogGraphTable myGraphTable;
  @NotNull private final DetailsPanel myDetailsPanel;
  @NotNull private final JBSplitter myDetailsSplitter;
  @NotNull private final VcsLogData myLogData;
  @NotNull private final FileHistoryUi myUi;

  @NotNull private Runnable myContainingBranchesListener;
  @NotNull private Runnable myMiniDetailsLoadedListener;

  public FileHistoryPanel(@NotNull FileHistoryUi ui, @NotNull VcsLogData logData, @NotNull VisiblePack visiblePack) {
    myUi = ui;
    myLogData = logData;
    myGraphTable = new VcsLogGraphTable(myUi, logData, visiblePack);
    myDetailsPanel = new DetailsPanel(logData, myUi.getColorManager(), this);
    myDetailsPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT));

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

    myDetailsSplitter = new OnePixelSplitter(true, "vcs.log.history.details.splitter.proportion", 0.7f);
    myDetailsSplitter.setFirstComponent(progressStripe);
    myDetailsSplitter.setSecondComponent(myUi.getProperties().get(MainVcsLogUiProperties.SHOW_DETAILS) ? myDetailsPanel : null);

    myDetailsPanel.installCommitSelectionListener(myGraphTable);
    updateWhenDetailsAreLoaded();

    setLayout(new BorderLayout());
    add(myDetailsSplitter, BorderLayout.CENTER);
    add(createActionsToolbar(), BorderLayout.WEST);

    PopupHandler.installPopupHandler(myGraphTable, VcsLogActionPlaces.HISTORY_POPUP_ACTION_GROUP, VcsLogActionPlaces.VCS_HISTORY_PLACE);

    Disposer.register(myUi, this);
  }

  @NotNull
  private JScrollPane setupScrolledGraph() {
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myGraphTable, SideBorder.LEFT);
    myGraphTable.viewportSet(scrollPane.getViewport());
    return scrollPane;
  }

  @NotNull
  private JComponent createActionsToolbar() {
    DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(ActionManager.getInstance().getAction(VcsLogActionPlaces.FILE_HISTORY_TOOLBAR_ACTION_GROUP));

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CHANGES_VIEW_TOOLBAR, toolbarGroup, false);
    toolbar.setTargetComponent(myGraphTable);
    return toolbar.getComponent();
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
    if (VcsLogInternalDataKeys.LOG_UI_PROPERTIES.is(dataId)) {
      return myUi.getProperties();
    }
    return null;
  }

  @Override
  public void dispose() {
    myLogData.getMiniDetailsGetter().removeDetailsLoadedListener(myMiniDetailsLoadedListener);
    myLogData.getContainingBranchesGetter().removeTaskCompletedListener(myContainingBranchesListener);

    myDetailsSplitter.dispose();
  }
}
