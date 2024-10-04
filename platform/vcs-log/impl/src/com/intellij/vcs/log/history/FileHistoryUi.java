// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.DataPackBase;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogStorage;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogContentUtil;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogNotificationIdsHolder;
import com.intellij.vcs.log.ui.highlighters.CurrentBranchHighlighter;
import com.intellij.vcs.log.ui.highlighters.VcsLogCommitsHighlighter;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.ui.table.column.TableColumnWidthProperty;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static com.intellij.ui.JBColor.namedColor;

public class FileHistoryUi extends AbstractVcsLogUi {
  private final @NotNull FilePath myPath;
  private final @Nullable Hash myRevision;

  private final @NotNull FileHistoryModel myFileHistoryModel;
  private final @NotNull FileHistoryUiProperties myUiProperties;
  private final @NotNull FileHistoryFilterUi myFilterUi;
  private final @NotNull FileHistoryPanel myFileHistoryPanel;

  @ApiStatus.Internal
  public FileHistoryUi(@NotNull VcsLogData logData,
                       @NotNull FileHistoryUiProperties uiProperties,
                       @NotNull VisiblePackRefresher refresher,
                       @NotNull FilePath path,
                       @Nullable Hash revision,
                       @NotNull VirtualFile root,
                       @NotNull String logId,
                       @NotNull VcsLogFilterCollection initialFilters,
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

    myFilterUi = new FileHistoryFilterUi(path, revision, root, uiProperties, logData, initialFilters, filters -> {
      refresher.onFiltersChange(filters);
    });
    myFileHistoryPanel = new FileHistoryPanel(this, myFileHistoryModel, myFilterUi, logData, path, root, myColorManager, this);

    getTable().addHighlighter(LOG_HIGHLIGHTER_FACTORY_EP.findExtensionOrFail(
      VcsLogCommitsHighlighter.Factory.class).createHighlighter(getLogData(), this));
    if (myRevision != null) {
      getTable().addHighlighter(new RevisionHistoryHighlighter(myLogData.getStorage(), myRevision, root));
    }
    else {
      getTable().addHighlighter(LOG_HIGHLIGHTER_FACTORY_EP.findExtensionOrFail(CurrentBranchHighlighter.Factory.class).createHighlighter(getLogData(), this));
    }

    myUiProperties.addChangeListener(new MyPropertiesChangeListener(), this);
  }

  public static @NotNull String getFileHistoryLogId(@NotNull FilePath path, @Nullable Hash revision) {
    return path.getPath() + (revision == null ? "" : ":" + revision.asString());
  }

  @Override
  public void setVisiblePack(@NotNull VisiblePack pack) {
    super.setVisiblePack(pack);

    myFilterUi.setVisiblePack(pack);

    if (pack.canRequestMore()) {
      requestMore(EmptyRunnable.INSTANCE);
    }
  }

  /**
   * @deprecated use {@link FileHistoryModel#getPathInCommit(Hash)} or {@link FileHistoryPaths#filePath(VcsLogDataPack, int)}
   */
  @Deprecated
  public @Nullable FilePath getPathInCommit(@NotNull Hash hash) {
    return myFileHistoryModel.getPathInCommit(hash);
  }

  @Override
  protected <T> void handleCommitNotFound(@NotNull T commitId, boolean commitExists,
                                          @NotNull BiFunction<? super VisiblePack, ? super T, Integer> rowGetter) {
    if (!commitExists) {
      super.handleCommitNotFound(commitId, false, rowGetter);
      return;
    }

    boolean hasBranchFilter = getFilterUi().hasBranchFilter();
    String text = VcsLogBundle.message(hasBranchFilter ? "file.history.commit.not.found.in.branch" : "file.history.commit.not.found",
                                       getCommitPresentation(commitId), myPath.getName());

    List<NotificationAction> actions = new ArrayList<>();
    if (hasBranchFilter && getFilterUi().isBranchFilterEnabled()) {
      actions.add(
        NotificationAction.createSimple(VcsLogBundle.message("file.history.commit.not.found.view.and.show.all.branches.link"), () -> {
          getFilterUi().clearFilters();
          VcsLogUtil.invokeOnChange(this, () -> jumpTo(commitId, rowGetter, SettableFuture.create(), false, true));
        }));
    }
    actions.add(NotificationAction.createSimple(VcsLogBundle.message("file.history.commit.not.found.view.in.log.link"), () -> {
      VcsLogContentUtil.runInMainLog(myProject, ui -> {
        ui.jumpTo(commitId, rowGetter, SettableFuture.create(), false, true);
      });
    }));
    VcsNotifier.getInstance(myProject).notifyWarning(VcsLogNotificationIdsHolder.COMMIT_NOT_FOUND, "", text,
                                                     actions.toArray(NotificationAction[]::new));
  }

  public boolean matches(@NotNull FilePath targetPath, @Nullable Hash targetRevision) {
    return myPath.equals(targetPath) && Objects.equals(myRevision, targetRevision);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull FileHistoryFilterUi getFilterUi() {
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

  public @NotNull JComponent getToolbar() {
    return myFileHistoryPanel.getToolbar();
  }

  @ApiStatus.Internal
  @Override
  public @NotNull FileHistoryUiProperties getProperties() {
    return myUiProperties;
  }

  private class MyPropertiesChangeListener implements VcsLogUiProperties.PropertiesChangeListener {
    @Override
    public <T> void onPropertyChanged(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
      if (CommonUiProperties.SHOW_DETAILS.equals(property)) {
        myFileHistoryPanel.showDetails(myUiProperties.get(CommonUiProperties.SHOW_DETAILS));
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

    private Predicate<Integer> myCondition;
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

      if (myCondition.test(commitId)) {
        return VcsCommitStyleFactory.background(myBgColor);
      }
      return VcsCommitStyle.DEFAULT;
    }

    private @NotNull Predicate<Integer> getCondition() {
      if (!(myVisiblePack instanceof VisiblePack)) return Predicates.alwaysFalse();
      DataPackBase dataPack = ((VisiblePack)myVisiblePack).getDataPack();
      if (!(dataPack instanceof DataPack)) return Predicates.alwaysFalse();
      Set<Integer> heads = Collections.singleton(myStorage.getCommitIndex(myRevision, myRoot));
      return ((DataPack)dataPack).getPermanentGraph().getContainedInBranchCondition(heads);
    }

    @Override
    public void update(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
      myVisiblePack = dataPack;
      if (myVisiblePack.getFilters().get(VcsLogFilterCollection.REVISION_FILTER) != null) {
        myCondition = Predicates.alwaysFalse();
      }
      else {
        myCondition = null;
      }
    }
  }
}
