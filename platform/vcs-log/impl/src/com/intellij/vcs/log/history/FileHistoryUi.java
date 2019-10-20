// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
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
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.highlighters.CurrentBranchHighlighter;
import com.intellij.vcs.log.ui.highlighters.MyCommitsHighlighter;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import com.intellij.vcs.log.ui.table.VcsLogColumn;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
  @NotNull private final Set<String> myHighlighterIds;
  @NotNull private final MyPropertiesChangeListener myPropertiesChangeListener;
  @NotNull private final History myHistory;

  public FileHistoryUi(@NotNull VcsLogData logData,
                       @NotNull FileHistoryUiProperties uiProperties,
                       @NotNull VisiblePackRefresher refresher,
                       @NotNull FilePath path,
                       @Nullable Hash revision,
                       @NotNull VirtualFile root) {
    super(getFileHistoryLogId(path, revision), logData, new FileHistoryColorManager(root, path), refresher);

    assert !path.isDirectory();

    myPath = path;
    myRoot = root;
    myRevision = revision;

    myUiProperties = uiProperties;
    myDiffHandler = notNull(logData.getLogProvider(root).getDiffHandler());

    myFilterUi = new FileHistoryFilterUi(path, revision, root, uiProperties);
    myFileHistoryPanel = new FileHistoryPanel(this, logData, path);

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

  @Override
  public void setVisiblePack(@NotNull VisiblePack pack) {
    super.setVisiblePack(pack);

    if (pack.canRequestMore()) {
      requestMore(EmptyRunnable.INSTANCE);
    }
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
    int commitIndex = myLogData.getStorage().getCommitIndex(hash, myRoot);
    return FileHistoryPaths.filePath(myVisiblePack, commitIndex);
  }

  private boolean isFileDeletedInCommit(@NotNull Hash hash) {
    int commitIndex = myLogData.getStorage().getCommitIndex(hash, myRoot);
    return FileHistoryPaths.isDeletedInCommit(myVisiblePack, commitIndex);
  }

  @Nullable
  public Change getSelectedChange() {
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
      showWarningWithLink(mainText, "View in Log", () -> {
        VcsLogContentUtil.openMainLogAndExecute(myProject, ui -> {
          if (commitId instanceof Hash) {
            ui.jumpToCommit((Hash)commitId, myRoot, SettableFuture.create());
          }
          else if (commitId instanceof String) {
            ui.jumpToCommitByPartOfHash((String)commitId, SettableFuture.create());
          }
        });
      });
    }
  }

  public boolean matches(@NotNull FilePath targetPath, @Nullable Hash targetRevision) {
    return myPath.equals(targetPath) && Objects.equals(myRevision, targetRevision);
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
    ((FileHistoryColorManager)myColorManager).update(myVisiblePack);
    myFileHistoryPanel.updateDataPack(myVisiblePack, permGraphChanged);
    myFileHistoryPanel.getGraphTable().rootColumnUpdated();
  }

  @NotNull
  @Override
  public VcsLogGraphTable getTable() {
    return myFileHistoryPanel.getGraphTable();
  }

  @NotNull
  @Override
  public Component getMainComponent() {
    return myFileHistoryPanel;
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
        myFileHistoryPanel.showDiffPreview(myUiProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW));
      }
      else if (CommonUiProperties.SHOW_ROOT_NAMES.equals(property)) {
        getTable().rootColumnUpdated();
      }
      else if (property.equals(CommonUiProperties.PREFER_COMMIT_DATE) && getTable().getTableColumn(VcsLogColumn.DATE) != null) {
        getTable().repaint();
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
