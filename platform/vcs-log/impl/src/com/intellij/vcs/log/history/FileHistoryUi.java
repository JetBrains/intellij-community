// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.NamedRunnable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.navigation.History;
import com.intellij.util.PairFunction;
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
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.ui.table.column.TableColumnWidthProperty;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static com.intellij.ui.JBColor.namedColor;

public class FileHistoryUi extends AbstractVcsLogUi {
  private static final @NotNull @NonNls String HELP_ID = "reference.versionControl.toolwindow.history";
  private final @NotNull FilePath myPath;
  private final @Nullable Hash myRevision;

  private final @NotNull FileHistoryModel myFileHistoryModel;
  private final @NotNull FileHistoryUiProperties myUiProperties;
  private final @NotNull FileHistoryFilterUi myFilterUi;
  private final @NotNull FileHistoryPanel myFileHistoryPanel;
  private final @NotNull MyPropertiesChangeListener myPropertiesChangeListener;
  private final @NotNull History myHistory;

  public FileHistoryUi(@NotNull VcsLogData logData,
                       @NotNull FileHistoryUiProperties uiProperties,
                       @NotNull VisiblePackRefresher refresher,
                       @NotNull FilePath path,
                       @Nullable Hash revision,
                       @NotNull VirtualFile root,
                       @NotNull String logId,
                       @NotNull VcsLogDiffHandler vcsLogDiffHandler) {
    super(logId, logData, new FileHistoryColorManager(root, path), refresher);

    assert !path.isDirectory();

    myPath = path;
    myRevision = revision;

    myUiProperties = uiProperties;

    myFileHistoryModel = new FileHistoryModel(logData, vcsLogDiffHandler, root) {
      @Override
      protected @NotNull VisiblePack getVisiblePack() {
        return myVisiblePack;
      }
    };

    myFilterUi = new FileHistoryFilterUi(path, revision, root, uiProperties);
    myFileHistoryPanel = new FileHistoryPanel(this, myFileHistoryModel, logData, path, root, this);

    getTable().addHighlighter(LOG_HIGHLIGHTER_FACTORY_EP.findExtensionOrFail(MyCommitsHighlighter.Factory.class).createHighlighter(getLogData(), this));
    if (myRevision != null) {
      getTable().addHighlighter(new RevisionHistoryHighlighter(myLogData.getStorage(), myRevision, root));
    }
    else {
      getTable().addHighlighter(LOG_HIGHLIGHTER_FACTORY_EP.findExtensionOrFail(CurrentBranchHighlighter.Factory.class).createHighlighter(getLogData(), this));
    }

    myPropertiesChangeListener = new MyPropertiesChangeListener();
    myUiProperties.addChangeListener(myPropertiesChangeListener);

    myHistory = VcsLogUiUtil.installNavigationHistory(this);
  }

  public static @NotNull String getFileHistoryLogId(@NotNull FilePath path, @Nullable Hash revision) {
    return path.getPath() + (revision == null ? "" : ":" + revision.asString());
  }

  @Override
  public void setVisiblePack(@NotNull VisiblePack pack) {
    super.setVisiblePack(pack);

    if (pack.canRequestMore()) {
      requestMore(EmptyRunnable.INSTANCE);
    }
  }

  public @Nullable FilePath getPathInCommit(@NotNull Hash hash) {
    return myFileHistoryModel.getPathInCommit(hash);
  }

  @Override
  protected <T> void handleCommitNotFound(@NotNull T commitId, boolean commitExists,
                                          @NotNull PairFunction<? super VisiblePack, ? super T, Integer> rowGetter) {
    if (!commitExists) {
      super.handleCommitNotFound(commitId, false, rowGetter);
      return;
    }

    boolean hasBranchFilter = getFilterUi().getFilters().get(VcsLogFilterCollection.BRANCH_FILTER) != null;
    String text = VcsLogBundle.message(hasBranchFilter ? "file.history.commit.not.found.in.branch" : "file.history.commit.not.found",
                                       getCommitPresentation(commitId), myPath.getName());

    List<NamedRunnable> actions = new ArrayList<>();
    if (hasBranchFilter) {
      actions.add(new NamedRunnable(VcsLogBundle.message("file.history.commit.not.found.view.and.show.all.branches.link")) {
        @Override
        public void run() {
          myUiProperties.set(FileHistoryUiProperties.SHOW_ALL_BRANCHES, true);
          invokeOnChange(() -> jumpTo(commitId, rowGetter, SettableFuture.create(), false, true));
        }
      });
    }
    actions.add(new NamedRunnable(VcsLogBundle.message("file.history.commit.not.found.view.in.log.link")) {
      @Override
      public void run() {
        VcsLogContentUtil.runInMainLog(myProject, ui -> {
          ui.jumpTo(commitId, rowGetter, SettableFuture.create(), false, true);
        });
      }
    });
    VcsBalloonProblemNotifier.showOverChangesView(myProject, text, MessageType.WARNING, actions.toArray(new NamedRunnable[0]));
  }

  public boolean matches(@NotNull FilePath targetPath, @Nullable Hash targetRevision) {
    return myPath.equals(targetPath) && Objects.equals(myRevision, targetRevision);
  }

  @Override
  public @NotNull VcsLogFilterUi getFilterUi() {
    return myFilterUi;
  }

  @Override
  protected void onVisiblePackUpdated(boolean permGraphChanged) {
    ((FileHistoryColorManager)myColorManager).update(myVisiblePack);
    myFileHistoryPanel.updateDataPack(myVisiblePack, permGraphChanged);
    myFileHistoryPanel.getGraphTable().rootColumnUpdated();
  }

  @Override
  public @NotNull VcsLogGraphTable getTable() {
    return myFileHistoryPanel.getGraphTable();
  }

  @Override
  public @NotNull JComponent getMainComponent() {
    return myFileHistoryPanel;
  }

  @Override
  public @Nullable String getHelpId() {
    return HELP_ID;
  }

  @Override
  public @NotNull FileHistoryUiProperties getProperties() {
    return myUiProperties;
  }

  @Override
  public @Nullable History getNavigationHistory() {
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
        myRefresher.onFiltersChange(myFilterUi.getFilters());
      }
      else if (CommonUiProperties.COLUMN_ID_ORDER.equals(property)) {
        getTable().onColumnOrderSettingChanged();
      }
      else if (property instanceof TableColumnWidthProperty) {
        getTable().forceReLayout(((TableColumnWidthProperty)property).getColumn());
      }
    }
  }

  private static class RevisionHistoryHighlighter implements VcsLogHighlighter {
    private final @NotNull JBColor myBgColor = namedColor("VersionControl.FileHistory.Commit.selectedBranchBackground",
                                                          new JBColor(new Color(0xfffee4), new Color(0x49493f)));
    private final @NotNull VcsLogStorage myStorage;
    private final @NotNull Hash myRevision;
    private final @NotNull VirtualFile myRoot;

    private @Nullable Condition<Integer> myCondition;
    private @NotNull VcsLogDataPack myVisiblePack = VisiblePack.EMPTY;

    RevisionHistoryHighlighter(@NotNull VcsLogStorage storage, @NotNull Hash revision, @NotNull VirtualFile root) {
      myStorage = storage;
      myRevision = revision;
      myRoot = root;
    }

    @Override
    public @NotNull VcsCommitStyle getStyle(int commitId, @NotNull VcsShortCommitDetails commitDetails, int column, boolean isSelected) {
      if (isSelected) return VcsCommitStyle.DEFAULT;

      if (myCondition == null) {
        myCondition = getCondition();
      }

      if (myCondition.value(commitId)) {
        return VcsCommitStyleFactory.background(myBgColor);
      }
      return VcsCommitStyle.DEFAULT;
    }

    private @NotNull Condition<Integer> getCondition() {
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
