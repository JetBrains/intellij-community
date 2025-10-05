// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame;

import com.intellij.diff.impl.DiffEditorViewer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.navigation.History;
import com.intellij.ui.progress.ProgressUIUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogNavigationUtil;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.details.CommitDetailsListPanel;
import com.intellij.vcs.log.ui.details.commit.CommitDetailsPanel;
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.ui.table.VcsLogTableCommitSelectionListener;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import kotlin.Unit;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.notNull;

public class MainFrame extends JPanel implements UiDataProvider, Disposable {
  private static final @NonNls String DIFF_SPLITTER_PROPORTION = "vcs.log.diff.splitter.proportion";
  private static final @NonNls String DETAILS_SPLITTER_PROPORTION = "vcs.log.details.splitter.proportion";
  private static final @NonNls String CHANGES_SPLITTER_PROPORTION = "vcs.log.changes.splitter.proportion";

  private final @NotNull MainVcsLogUiProperties myUiProperties;

  private final @NotNull JComponent myToolbar;
  private final @NotNull VcsLogGraphTable myGraphTable;

  private final @NotNull VcsLogFilterUiEx myFilterUi;

  private final @NotNull VcsLogAsyncChangesTreeModel myChangesTreeModel;
  private final @NotNull VcsLogChangesBrowser myChangesBrowser;
  private final @NotNull Splitter myChangesBrowserSplitter;

  private final @NotNull CommitDetailsListPanel myDetailsPanel;
  private final @NotNull Splitter myDetailsSplitter;
  private final @NotNull EditorNotificationPanel myNotificationLabel;

  private final @NotNull History myHistory;

  private boolean myIsLoading;
  private @Nullable FilePath myPathToSelect = null;

  private final @NotNull FrameDiffPreview myDiffPreview;

  public MainFrame(@NotNull VcsLogData logData,
                   @NotNull AbstractVcsLogUi logUi,
                   @NotNull MainVcsLogUiProperties uiProperties,
                   @NotNull VcsLogFilterUiEx filterUi,
                   @NotNull VcsLogColorManager colorManager,
                   boolean withEditorDiffPreview,
                   @NotNull Disposable disposable) {
    myUiProperties = uiProperties;

    myFilterUi = filterUi;

    myGraphTable = VcsLogComponents.createTable(logData, logUi, filterUi, colorManager, disposable);

    myDetailsPanel = new CommitDetailsListPanel(logData.getProject(), this, () -> {
      return new CommitDetailsPanel(commit -> {
        VcsLogNavigationUtil.jumpToCommit(logUi, commit.getHash(), commit.getRoot(), false, true);
        return Unit.INSTANCE;
      });
    });

    CommitDetailsLoader<VcsFullCommitDetails> commitDetailsLoader = new CommitDetailsLoader<>(logData.getCommitDetailsGetter(), this);

    VcsLogCommitSelectionListenerForDetails listenerForDetails =
      new VcsLogCommitSelectionListenerForDetails(logData, colorManager, myDetailsPanel, this);
    commitDetailsLoader.addListener(listenerForDetails);

    myChangesTreeModel = new VcsLogAsyncChangesTreeModel(logData, myUiProperties, this);
    myChangesBrowser = new VcsLogChangesBrowser(logData.getProject(), myChangesTreeModel, this);
    myChangesBrowser.setShowDiffActionPreview(withEditorDiffPreview ? new VcsLogEditorDiffPreview(myChangesBrowser) : null);
    myChangesBrowser.getDiffAction().registerCustomShortcutSet(myChangesBrowser.getDiffAction().getShortcutSet(), getGraphTable());
    JBLoadingPanel changesLoadingPane = new JBLoadingPanel(new BorderLayout(), this, ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS) {
      @Override
      public Dimension getMinimumSize() {
        return VcsLogUiUtil.expandToFitToolbar(super.getMinimumSize(), myChangesBrowser.getToolbar().getComponent());
      }
    };
    changesLoadingPane.add(myChangesBrowser);

    myToolbar = createActionsToolbar();
    myChangesBrowser.setToolbarHeightReferent(myToolbar);

    VcsLogCommitSelectionListenerForDiff commitSelectionListener =
      new VcsLogCommitSelectionListenerForDiff(changesLoadingPane, myChangesTreeModel) {
        @Override
        public void onLoadingStopped() {
          super.onLoadingStopped();
          myIsLoading = false;
          if (myPathToSelect != null) {
            myChangesBrowser.selectFile(myPathToSelect);
            myPathToSelect = null;
          }
        }
      };

    commitDetailsLoader.addListener(commitSelectionListener);

    VcsLogTableCommitSelectionListener tableCommitSelectionListener = new VcsLogTableCommitSelectionListener(myGraphTable) {
      @Override
      protected void handleSelection(@NotNull List<@NotNull Integer> commitIds) {
        commitDetailsLoader.loadDetails(commitIds);
      }

      @Override
      protected void onHandlingScheduled() {
        myIsLoading = true;
        myPathToSelect = null;
      }
    };

    myGraphTable.getSelectionModel().addListSelectionListener(tableCommitSelectionListener);
    Disposer.register(disposable, () -> myGraphTable.getSelectionModel().removeListSelectionListener(tableCommitSelectionListener));

    myNotificationLabel = new EditorNotificationPanel(UIUtil.getPanelBackground(), EditorNotificationPanel.Status.Warning);
    myNotificationLabel.setVisible(false);
    myNotificationLabel.setBorder(new CompoundBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                                                     notNull(myNotificationLabel.getBorder(), JBUI.Borders.empty())));

    JComponent toolbars = new BorderLayoutPanel();
    toolbars.add(myToolbar, BorderLayout.NORTH);
    toolbars.add(myNotificationLabel, BorderLayout.CENTER);
    JComponent toolbarsAndTable = new JPanel(new BorderLayout());
    toolbarsAndTable.add(toolbars, BorderLayout.NORTH);

    JComponent tableWithProgress = VcsLogUiUtil.installScrollingAndProgress(myGraphTable, this);
    toolbarsAndTable.add(tableWithProgress, BorderLayout.CENTER);

    myDetailsSplitter = new OnePixelSplitter(true, DETAILS_SPLITTER_PROPORTION, 0.7f);
    myDetailsSplitter.setFirstComponent(changesLoadingPane);
    showDetails(myUiProperties.get(CommonUiProperties.SHOW_DETAILS));

    myChangesBrowserSplitter = new OnePixelSplitter(false, CHANGES_SPLITTER_PROPORTION, 0.7f);
    myChangesBrowserSplitter.setFirstComponent(toolbarsAndTable);
    myChangesBrowserSplitter.setSecondComponent(myDetailsSplitter);

    setLayout(new BorderLayout());
    myDiffPreview = new FrameDiffPreview(myUiProperties, myChangesBrowserSplitter, DIFF_SPLITTER_PROPORTION, 0.7f, this) {
      @Override
      protected @NotNull DiffEditorViewer createViewer() {
        DiffEditorViewer processor = myChangesBrowser.createChangeProcessor(false);
        processor.setToolbarVerticalSizeReferent(getToolbar());
        return processor;
      }
    };
    add(myDiffPreview.getMainComponent());

    myHistory = VcsLogUiUtil.installNavigationHistory(logUi, myGraphTable);

    Disposer.register(disposable, this);

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
    myChangesTreeModel.setAffectedPaths(VcsLogUtil.getAffectedPaths(dataPack));
  }

  public @NotNull VcsLogGraphTable getGraphTable() {
    return myGraphTable;
  }

  public @NotNull VcsLogFilterUiEx getFilterUi() {
    return myFilterUi;
  }

  protected @NotNull JComponent createActionsToolbar() {
    return VcsLogComponents.createActionsToolbar(myGraphTable, myFilterUi);
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    Change[] changes = myChangesTreeModel.getChanges().toArray(Change.EMPTY_CHANGE_ARRAY);
    sink.set(VcsDataKeys.CHANGES, changes);
    sink.set(VcsDataKeys.SELECTED_CHANGES, changes);
    VcsLogComponents.collectLogKeys(sink, myUiProperties, myGraphTable, myHistory, myFilterUi, myToolbar, this);
  }

  public @NotNull JComponent getToolbar() {
    return myToolbar;
  }

  public @NotNull ChangesBrowserBase getChangesBrowser() {
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

  private class MyFocusPolicy extends ComponentsListFocusTraversalPolicy {
    @Override
    protected @NotNull List<Component> getOrderedComponents() {
      return ContainerUtil.skipNulls(
        Arrays.asList(myGraphTable,
                      myChangesBrowser.getPreferredFocusedComponent(),
                      myDiffPreview.getPreferredFocusedComponent(),
                      myFilterUi.getTextFilterComponent().getFocusedComponent())
      );
    }
  }
}
