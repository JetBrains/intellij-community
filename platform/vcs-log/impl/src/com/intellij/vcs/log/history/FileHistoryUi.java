// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.navigation.History;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.DataPackBase;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogStorage;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogContentUtil;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.highlighters.CurrentBranchHighlighter;
import com.intellij.vcs.log.ui.highlighters.MyCommitsHighlighter;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.ui.JBColor.namedColor;
import static com.intellij.util.ObjectUtils.notNull;

public class FileHistoryUi extends AbstractVcsLogUi {
  @NotNull private static final String HELP_ID = "reference.versionControl.toolwindow.history";
  @NotNull private final FilePath myPath;
  @NotNull private final VirtualFile myRoot;
  @Nullable private final Hash myRevision;

  @NotNull private final VcsLogDiffHandler myDiffHandler;
  @NotNull private final FileHistoryUiProperties myUiProperties;
  @NotNull private final FileHistoryFilterUi myFilterUi;
  @NotNull private final FileHistoryPanel myFileHistoryPanel;
  @Nullable private final FileHistoryDiffPreview myDiffPreview;
  @Nullable private final OnePixelSplitter myDiffPreviewSplitter;
  @NotNull private final JComponent myMainComponent;
  @NotNull private final Set<String> myHighlighterIds;
  @NotNull private final MyPropertiesChangeListener myPropertiesChangeListener;
  @NotNull private final History myHistory;

  public FileHistoryUi(@NotNull VcsLogData logData,
                       @NotNull VcsLogColorManager manager,
                       @NotNull FileHistoryUiProperties uiProperties,
                       @NotNull VisiblePackRefresher refresher,
                       @NotNull FilePath path,
                       @Nullable Hash revision,
                       @NotNull VirtualFile root) {
    super(getFileHistoryLogId(path, revision), logData, manager, refresher);

    myPath = path;
    myRoot = root;
    myRevision = revision;

    myUiProperties = uiProperties;
    myDiffHandler = notNull(logData.getLogProvider(root).getDiffHandler());

    myFilterUi = new FileHistoryFilterUi(path, revision, root, uiProperties);
    myFileHistoryPanel = new FileHistoryPanel(this, logData, myVisiblePack, path);

    if (!myPath.isDirectory()) {
      myDiffPreview = new FileHistoryDiffPreview(myProject, () -> getSelectedChange(), this);
      ListSelectionListener selectionListener = e -> {
        int[] selection = getTable().getSelectedRows();
        ApplicationManager.getApplication()
          .invokeLater(() -> myDiffPreview.updatePreview(myUiProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW)),
                       o -> !Arrays.equals(selection, getTable().getSelectedRows()));
      };
      getTable().getSelectionModel().addListSelectionListener(selectionListener);

      myDiffPreviewSplitter = new OnePixelSplitter(false, "vcs.history.diff.splitter.proportion", 0.7f);
      myDiffPreviewSplitter.setHonorComponentsMinimumSize(false);
      myDiffPreviewSplitter.setFirstComponent(myFileHistoryPanel);
      showDiffPreview(myUiProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW));
      myMainComponent = myDiffPreviewSplitter;
    }
    else {
      myDiffPreview = null;
      myDiffPreviewSplitter = null;
      myMainComponent = myFileHistoryPanel;
    }

    myHighlighterIds = myRevision == null
                       ? ContainerUtil.newHashSet(MyCommitsHighlighter.Factory.ID,
                                                  CurrentBranchHighlighter.Factory.ID)
                       : Collections.singleton(MyCommitsHighlighter.Factory.ID);
    for (VcsLogHighlighterFactory factory : ContainerUtil.filter(LOG_HIGHLIGHTER_FACTORY_EP.getExtensions(myProject),
                                                                 f -> isHighlighterEnabled(f.getId()))) {
      getTable().addHighlighter(factory.createHighlighter(logData, this));
    }
    if (myRevision != null) {
      getTable().addHighlighter(new RevisionHistoryHighlighter(myLogData.getStorage(), myRevision, myRoot));
    }

    myPropertiesChangeListener = new MyPropertiesChangeListener();
    myUiProperties.addChangeListener(myPropertiesChangeListener);

    myHistory = VcsLogUiUtil.installNavigationHistory(this);
  }

  @NotNull
  public static String getFileHistoryLogId(@NotNull FilePath path, @Nullable Hash revision) {
    return path.getPath() + (revision == null ? "" : revision.asString());
  }

  public boolean hasDiffPreview() {
    return myDiffPreview != null;
  }

  @Nullable
  public VcsFileRevision createRevision(@Nullable VcsCommitMetadata commit) {
    if (commit == null) return null;
    if (isFileDeletedInCommit(commit.getId())) return VcsFileRevision.NULL;
    FilePath path = getPathInCommit(commit.getId());
    if (path == null) return null;
    return new VcsLogFileRevision(commit, myDiffHandler.createContentRevision(path, commit.getId()), path, false);
  }

  @Nullable
  public FilePath getPathInCommit(@NotNull Hash hash) {
    if (myPath.isDirectory()) return myPath;
    int commitIndex = myLogData.getStorage().getCommitIndex(hash, myRoot);
    return FileHistoryVisiblePack.filePath(myVisiblePack, commitIndex);
  }

  private boolean isFileDeletedInCommit(@NotNull Hash hash) {
    if (myPath.isDirectory()) return false;

    int commitIndex = myLogData.getStorage().getCommitIndex(hash, myRoot);
    return FileHistoryVisiblePack.isDeletedInCommit(myVisiblePack, commitIndex);
  }

  @NotNull
  List<Change> collectRelevantChanges(@NotNull VcsFullCommitDetails details) {
    FilePath filePath = getPathInCommit(details.getId());
    if (filePath == null) return ContainerUtil.emptyList();
    return FileHistoryUtil.collectRelevantChanges(details,
                                                  change -> filePath.isDirectory()
                                                            ? FileHistoryUtil.affectsDirectory(change, filePath)
                                                            : FileHistoryUtil
                                                              .affectsFile(change, filePath, isFileDeletedInCommit(details.getId())));
  }

  @Nullable
  public Change getSelectedChange() {
    if (myPath.isDirectory()) return null;

    int[] rows = getTable().getSelectedRows();
    if (rows.length == 0) return null;
    int row = rows[0];
    List<Integer> parentRows;
    if (rows.length == 1) {
      parentRows = myVisiblePack.getVisibleGraph().getRowInfo(row).getAdjacentRows(true);
    }
    else {
      parentRows = Collections.singletonList(rows[rows.length - 1]);
    }
    return FileHistoryUtil.createChangeToParents(row, parentRows, myVisiblePack, myDiffHandler, myLogData);
  }

  @Override
  protected <T> void handleCommitNotFound(@NotNull T commitId, boolean commitExists,
                                          @NotNull PairFunction<GraphTableModel, T, Integer> rowGetter) {
    if (!commitExists) {
      super.handleCommitNotFound(commitId, false, rowGetter);
      return;
    }

    String mainText = "Commit " + getCommitPresentation(commitId) + " does not exist in history for " + myPath.getName();
    if (getFilterUi().getFilters().get(VcsLogFilterCollection.BRANCH_FILTER) != null) {
      showWarningWithLink(mainText + " in current branch", "View and Show All Branches", () -> {
        myUiProperties.set(FileHistoryUiProperties.SHOW_ALL_BRANCHES, true);
        invokeOnChange(() -> jumpTo(commitId, rowGetter, SettableFuture.create()));
      });
    }
    else {
      VcsLogUiImpl mainLogUi = VcsProjectLog.getInstance(myProject).getMainLogUi();
      if (mainLogUi != null) {
        showWarningWithLink(mainText, "View in Log", () -> {
          if (VcsLogContentUtil.selectLogUi(myProject, mainLogUi)) {
            if (commitId instanceof Hash) {
              mainLogUi.jumpToCommit((Hash)commitId,
                                     myRoot,
                                     SettableFuture.create());
            }
            else if (commitId instanceof String) {
              mainLogUi.jumpToCommitByPartOfHash((String)commitId, SettableFuture.create());
            }
          }
        });
      }
    }
  }

  public void jumpToNearestCommit(@NotNull Hash hash) {
    jumpTo(hash, (model, h) -> {
      if (!myLogData.getStorage().containsCommit(new CommitId(h, myRoot))) return GraphTableModel.COMMIT_NOT_FOUND;
      int commitIndex = myLogData.getCommitIndex(h, myRoot);
      Integer rowIndex = myVisiblePack.getVisibleGraph().getVisibleRowIndex(commitIndex);
      if (rowIndex == null) {
        rowIndex = ReachableNodesUtilKt.findVisibleAncestorRow(commitIndex, myVisiblePack);
      }
      return rowIndex == null ? GraphTableModel.COMMIT_DOES_NOT_MATCH : rowIndex;
    }, SettableFuture.create());
  }

  public boolean matches(@NotNull FilePath targetPath, @Nullable Hash targetRevision) {
    return myPath.equals(targetPath) && Objects.equals(myRevision, targetRevision);
  }

  private void showDiffPreview(boolean state) {
    if (myDiffPreview != null) {
      myDiffPreview.updatePreview(state);
      myDiffPreviewSplitter.setSecondComponent(state ? myDiffPreview.getComponent() : null);
    }
  }

  @NotNull
  @Override
  public VcsLogFilterUi getFilterUi() {
    return myFilterUi;
  }

  @Override
  public boolean isHighlighterEnabled(@NotNull String id) {
    return myHighlighterIds.contains(id);
  }

  @Override
  protected void onVisiblePackUpdated(boolean permGraphChanged) {
    myFileHistoryPanel.updateDataPack(myVisiblePack, permGraphChanged);
    if (myDiffPreview != null) {
      myDiffPreview.updatePreview(myUiProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW));
    }
  }

  @NotNull
  @Override
  public VcsLogGraphTable getTable() {
    return myFileHistoryPanel.getGraphTable();
  }

  @NotNull
  @Override
  public Component getMainComponent() {
    return myMainComponent;
  }

  @Nullable
  @Override
  public String getHelpId() {
    return HELP_ID;
  }

  private void updateFilter() {
    myRefresher.onFiltersChange(myFilterUi.getFilters());
  }

  @Override
  @NotNull
  public FileHistoryUiProperties getProperties() {
    return myUiProperties;
  }

  @Nullable
  @Override
  public History getNavigationHistory() {
    return myHistory;
  }

  @Override
  public void dispose() {
    myUiProperties.removeChangeListener(myPropertiesChangeListener);
    super.dispose();
  }

  private class MyPropertiesChangeListener implements VcsLogUiProperties.PropertiesChangeListener {
    @Override
    public <T> void onPropertyChanged(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
      if (CommonUiProperties.SHOW_DETAILS.equals(property)) {
        myFileHistoryPanel.showDetails(myUiProperties.get(CommonUiProperties.SHOW_DETAILS));
      }
      else if (FileHistoryUiProperties.SHOW_ALL_BRANCHES.equals(property)) {
        updateFilter();
      }
      else if (CommonUiProperties.COLUMN_ORDER.equals(property)) {
        getTable().onColumnOrderSettingChanged();
      }
      else if (property instanceof CommonUiProperties.TableColumnProperty) {
        getTable().forceReLayout(((CommonUiProperties.TableColumnProperty)property).getColumn());
      }
      else if (CommonUiProperties.SHOW_DIFF_PREVIEW.equals(property)) {
        showDiffPreview(myUiProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW));
      }
    }
  }

  private static class RevisionHistoryHighlighter implements VcsLogHighlighter {
    @NotNull private final JBColor myBgColor = namedColor("VersionControl.FileHistory.Commit.selectedBranchBackground",
                                                          new JBColor(new Color(0xfffee4), new Color(0x49493f)));
    @NotNull private final VcsLogStorage myStorage;
    @NotNull private final Hash myRevision;
    @NotNull private final VirtualFile myRoot;

    @Nullable private Condition<Integer> myCondition;
    @NotNull private VcsLogDataPack myVisiblePack = VisiblePack.EMPTY;

    RevisionHistoryHighlighter(@NotNull VcsLogStorage storage, @NotNull Hash revision, @NotNull VirtualFile root) {
      myStorage = storage;
      myRevision = revision;
      myRoot = root;
    }

    @NotNull
    @Override
    public VcsCommitStyle getStyle(int commitId, @NotNull VcsShortCommitDetails commitDetails, boolean isSelected) {
      if (isSelected) return VcsCommitStyle.DEFAULT;

      if (myCondition == null) {
        myCondition = getCondition();
      }

      if (myCondition.value(commitId)) {
        return VcsCommitStyleFactory.background(myBgColor);
      }
      return VcsCommitStyle.DEFAULT;
    }

    @NotNull
    private Condition<Integer> getCondition() {
      if (!(myVisiblePack instanceof VisiblePack)) return Conditions.alwaysFalse();
      DataPackBase dataPack = ((VisiblePack)myVisiblePack).getDataPack();
      if (!(dataPack instanceof DataPack)) return Conditions.alwaysFalse();
      Set<Integer> heads = Collections.singleton(myStorage.getCommitIndex(myRevision, myRoot));
      return ((DataPack)dataPack).getPermanentGraph().getContainedInBranchCondition(heads);
    }

    @Override
    public void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
      myVisiblePack = dataPack;
      if (myVisiblePack.getFilters().get(VcsLogFilterCollection.REVISION_FILTER) != null) {
        myCondition = Conditions.alwaysFalse();
      }
      else {
        myCondition = null;
      }
    }
  }
}
