// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.VcsLogNavigationUtil;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.CompoundVisibleGraph;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.VisiblePackChangeListener;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

public abstract class AbstractVcsLogUi extends VcsLogUiBase implements Disposable {
  private static final Logger LOG = Logger.getInstance(AbstractVcsLogUi.class);
  public static final ExtensionPointName<VcsLogHighlighterFactory> LOG_HIGHLIGHTER_FACTORY_EP =
    ExtensionPointName.create("com.intellij.logHighlighterFactory");

  protected final @NotNull Project myProject;
  protected final @NotNull VcsLogColorManager myColorManager;

  protected final @NotNull VisiblePackChangeListener myVisiblePackChangeListener;

  protected volatile @NotNull VisiblePack myVisiblePack = VisiblePack.EMPTY;

  public AbstractVcsLogUi(@NotNull String id,
                          @NotNull VcsLogData logData,
                          @NotNull VcsLogColorManager manager,
                          @NotNull VisiblePackRefresher refresher) {
    super(id, logData, refresher);
    myProject = logData.getProject();
    myColorManager = manager;

    myVisiblePackChangeListener = visiblePack -> UIUtil.invokeLaterIfNeeded(() -> {
      if (!myDisposableFlag.isDisposed()) {
        setVisiblePack(visiblePack);
      }
    });
    myRefresher.addVisiblePackChangeListener(myVisiblePackChangeListener);
  }

  public void setVisiblePack(@NotNull VisiblePack pack) {
    ThreadingAssertions.assertEventDispatchThread();

    boolean permGraphChanged =
      pack.getVisibleGraph() instanceof CompoundVisibleGraph
      || myVisiblePack.getDataPack() != pack.getDataPack();

    myVisiblePack = pack;

    onVisiblePackUpdated(permGraphChanged);

    fireChangeEvent(myVisiblePack, permGraphChanged);
    getTable().repaint();
  }

  protected abstract void onVisiblePackUpdated(boolean permGraphChanged);

  public @NotNull VcsLogColorManager getColorManager() {
    return myColorManager;
  }

  @Override
  public abstract @NotNull VcsLogGraphTable getTable();

  public void requestMore(@NotNull Runnable onLoaded) {
    VcsLogUtil.requestToLoadMore(this, onLoaded);
    getTable().setPaintBusy(true);
  }

  @Override
  public @NotNull VisiblePack getDataPack() {
    return myVisiblePack;
  }

  @Override
  @ApiStatus.Internal
  public <T> JumpResult jumpToSync(@NotNull T commitId,
                                   @NotNull BiFunction<? super VisiblePack, ? super T, Integer> rowGetter,
                                   boolean silently,
                                   boolean focus) {
    int result = rowGetter.apply(myVisiblePack, commitId);
    if (result >= 0) {
      getTable().jumpToRow(result, focus);
    }

    JumpResult jumpResult = JumpResult.fromInt(result);
    if (!silently && jumpResult != JumpResult.SUCCESS) {
      handleCommitNotFound(commitId, jumpResult == JumpResult.COMMIT_DOES_NOT_MATCH, rowGetter);
    }
    return jumpResult;
  }

  @Override
  public <T> void jumpTo(@NotNull T commitId,
                         @NotNull BiFunction<? super VisiblePack, ? super T, Integer> rowGetter,
                         @NotNull SettableFuture<JumpResult> future,
                         boolean silently,
                         boolean focus) {
    if (!silently) {
      future.addListener(() -> {
        try {
          JumpResult result = future.get();
          if (result != JumpResult.SUCCESS) {
            handleCommitNotFound(commitId, result == JumpResult.COMMIT_DOES_NOT_MATCH, rowGetter);
          }
        }
        catch (InterruptedException | ExecutionException | CancellationException ignore) {
        }
      }, MoreExecutors.directExecutor());
    }

    tryJumpTo(commitId, rowGetter, future, focus);
  }

  /**
   * @see VcsLogNavigationUtil for public usages
   */
  public <T> void tryJumpTo(@NotNull T commitId,
                            @NotNull BiFunction<? super VisiblePack, ? super T, Integer> rowGetter,
                            @NotNull SettableFuture<JumpResult> future,
                            boolean focus) {
    if (future.isCancelled()) return;

    int result = rowGetter.apply(myVisiblePack, commitId);
    if (result >= 0) {
      getTable().jumpToRow(result, focus);
      future.set(JumpResult.SUCCESS);
    }
    else if (VcsLogUtil.canRequestMore(myVisiblePack)) {
      VcsLogUtil.requestToLoadMore(this, () -> tryJumpTo(commitId, rowGetter, future, focus));
    }
    else if (myLogData.getDataPack() != myVisiblePack.getDataPack() ||
             (myVisiblePack.canRequestMore() && VcsLogUtil.isMoreRequested(myVisiblePack))) {
      VcsLogUtil.invokeOnChange(this, () -> tryJumpTo(commitId, rowGetter, future, focus));
    }
    else if (myVisiblePack.getDataPack() instanceof DataPack.ErrorDataPack ||
             myVisiblePack instanceof VisiblePack.ErrorVisiblePack) {
      future.set(JumpResult.fromInt(result));
    }
    else if (!myVisiblePack.isFull()) {
      VcsLogUtil.invokeOnChange(this, () -> tryJumpTo(commitId, rowGetter, future, focus));
    }
    else {
      future.set(JumpResult.fromInt(result));
    }
  }

  protected <T> void handleCommitNotFound(@NotNull T commitId,
                                          boolean commitExists,
                                          @NotNull BiFunction<? super VisiblePack, ? super T, Integer> rowGetter) {
    String message = getCommitNotFoundMessage(commitId, commitExists);
    VcsNotifier.getInstance(myProject).notifyWarning(VcsLogNotificationIdsHolder.COMMIT_NOT_FOUND, "", message);
  }

  protected static @NotNull @Nls <T> String getCommitNotFoundMessage(@NotNull T commitId, boolean exists) {
    String commitPresentation = getCommitPresentation(commitId);
    return exists ? VcsLogBundle.message("vcs.log.commit.does.not.match", commitPresentation) :
           VcsLogBundle.message("vcs.log.commit.not.found", commitPresentation);
  }

  protected static @NotNull <T> String getCommitPresentation(@NotNull T commitId) {
    Hash hash = getCommitHash(commitId);
    if (hash != null) {
      return VcsLogBundle.message("vcs.log.commit.prefix", hash.toShortString());
    }
    if (commitId instanceof String commitString) {
      if (VcsLogUtil.HASH_PREFIX_REGEX.matcher(commitString).matches()) {
        return VcsLogBundle.message("vcs.log.commit.or.reference.prefix", VcsLogUtil.getShortHash(commitString));
      }
    }
    return VcsLogBundle.message("vcs.log.commit.or.reference.prefix", commitId.toString());
  }

  protected static <T> @Nullable Hash getCommitHash(@NotNull T commitId) {
    if (commitId instanceof Hash hash) {
      return hash;
    }
    if (commitId instanceof CommitId id) {
      return id.getHash();
    }
    return null;
  }

  @Override
  public void dispose() {
    ThreadingAssertions.assertEventDispatchThread();
    LOG.debug("Disposing VcsLogUi '" + getId() + "'");
    myRefresher.removeVisiblePackChangeListener(myVisiblePackChangeListener);
    getTable().removeAllHighlighters();
    myVisiblePack = VisiblePack.EMPTY;
  }
}
