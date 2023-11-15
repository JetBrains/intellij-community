// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.util.PairFunction;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.VcsLogImpl;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.CompoundVisibleGraph;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.VisiblePackChangeListener;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public abstract class AbstractVcsLogUi implements VcsLogUiEx, Disposable {
  private static final Logger LOG = Logger.getInstance(AbstractVcsLogUi.class);
  public static final ExtensionPointName<VcsLogHighlighterFactory> LOG_HIGHLIGHTER_FACTORY_EP =
    ExtensionPointName.create("com.intellij.logHighlighterFactory");

  private final @NotNull String myId;
  protected final @NotNull Project myProject;
  protected final @NotNull VcsLogData myLogData;
  protected final @NotNull VcsLogColorManager myColorManager;
  protected final @NotNull VcsLogImpl myLog;
  protected final @NotNull VisiblePackRefresher myRefresher;
  protected final @NotNull CheckedDisposable myDisposableFlag = Disposer.newCheckedDisposable();

  protected final @NotNull Collection<VcsLogListener> myLogListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  protected final @NotNull VisiblePackChangeListener myVisiblePackChangeListener;

  protected volatile @NotNull VisiblePack myVisiblePack = VisiblePack.EMPTY;

  public AbstractVcsLogUi(@NotNull String id,
                          @NotNull VcsLogData logData,
                          @NotNull VcsLogColorManager manager,
                          @NotNull VisiblePackRefresher refresher) {
    myId = id;
    myProject = logData.getProject();
    myLogData = logData;
    myRefresher = refresher;
    myColorManager = manager;

    Disposer.register(this, myRefresher);
    Disposer.register(this, myDisposableFlag);

    myLog = new VcsLogImpl(logData, this);
    myVisiblePackChangeListener = visiblePack -> UIUtil.invokeLaterIfNeeded(() -> {
      if (!myDisposableFlag.isDisposed()) {
        setVisiblePack(visiblePack);
      }
    });
    myRefresher.addVisiblePackChangeListener(myVisiblePackChangeListener);
  }

  @Override
  public @NotNull String getId() {
    return myId;
  }

  public void setVisiblePack(@NotNull VisiblePack pack) {
    ThreadingAssertions.assertEventDispatchThread();

    boolean permGraphChanged =
      pack.getVisibleGraph() instanceof CompoundVisibleGraph
      || myVisiblePack.getDataPack() != pack.getDataPack();

    myVisiblePack = pack;

    onVisiblePackUpdated(permGraphChanged);

    fireFilterChangeEvent(myVisiblePack, permGraphChanged);
    getTable().repaint();
  }

  protected abstract void onVisiblePackUpdated(boolean permGraphChanged);

  @Override
  public @NotNull VisiblePackRefresher getRefresher() {
    return myRefresher;
  }

  public @NotNull VcsLogColorManager getColorManager() {
    return myColorManager;
  }

  @Override
  public @NotNull VcsLog getVcsLog() {
    return myLog;
  }

  @Override
  public @NotNull VcsLogData getLogData() {
    return myLogData;
  }

  @Override
  public abstract @NotNull VcsLogGraphTable getTable();

  public void requestMore(@NotNull Runnable onLoaded) {
    myRefresher.moreCommitsNeeded(onLoaded);
    getTable().setPaintBusy(true);
  }

  @Override
  public @NotNull VisiblePack getDataPack() {
    return myVisiblePack;
  }

  @Override
  public <T> void jumpTo(@NotNull T commitId,
                         @NotNull PairFunction<? super VisiblePack, ? super T, Integer> rowGetter,
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

  public <T> void tryJumpTo(@NotNull T commitId,
                            @NotNull PairFunction<? super VisiblePack, ? super T, Integer> rowGetter,
                            @NotNull SettableFuture<JumpResult> future,
                            boolean focus) {
    if (future.isCancelled()) return;

    GraphTableModel model = getTable().getModel();

    int result = rowGetter.fun(myVisiblePack, commitId);
    if (result >= 0) {
      getTable().jumpToRow(result, focus);
      future.set(JumpResult.SUCCESS);
    }
    else if (model.canRequestMore()) {
      model.requestToLoadMore(() -> tryJumpTo(commitId, rowGetter, future, focus));
    }
    else if (myLogData.getDataPack() != myVisiblePack.getDataPack()) {
      invokeOnChange(() -> tryJumpTo(commitId, rowGetter, future, focus));
    }
    else if (myVisiblePack.getDataPack() instanceof DataPack.ErrorDataPack ||
             myVisiblePack instanceof VisiblePack.ErrorVisiblePack) {
      future.set(JumpResult.fromInt(result));
    }
    else if (!myVisiblePack.isFull()) {
      invokeOnChange(() -> tryJumpTo(commitId, rowGetter, future, focus));
    }
    else {
      future.set(JumpResult.fromInt(result));
    }
  }

  protected <T> void handleCommitNotFound(@NotNull T commitId,
                                          boolean commitExists,
                                          @NotNull PairFunction<? super VisiblePack, ? super T, Integer> rowGetter) {
    String message = getCommitNotFoundMessage(commitId, commitExists);
    VcsNotifier.getInstance(myProject).notifyWarning(VcsLogNotificationIdsHolder.COMMIT_NOT_FOUND, "", message);
  }

  protected static @NotNull @Nls <T> String getCommitNotFoundMessage(@NotNull T commitId, boolean exists) {
    String commitPresentation = getCommitPresentation(commitId);
    return exists ? VcsLogBundle.message("vcs.log.commit.does.not.match", commitPresentation) :
           VcsLogBundle.message("vcs.log.commit.not.found", commitPresentation);
  }

  protected static @NotNull <T> String getCommitPresentation(@NotNull T commitId) {
    if (commitId instanceof Hash) {
      return VcsLogBundle.message("vcs.log.commit.prefix", ((Hash)commitId).toShortString());
    }
    if (commitId instanceof String commitString) {
      if (VcsLogUtil.HASH_PREFIX_REGEX.matcher(commitString).matches()) {
        return VcsLogBundle.message("vcs.log.commit.or.reference.prefix", VcsLogUtil.getShortHash(commitString));
      }
    }
    return VcsLogBundle.message("vcs.log.commit.or.reference.prefix", commitId.toString());
  }

  @Override
  public void addLogListener(@NotNull VcsLogListener listener) {
    ThreadingAssertions.assertEventDispatchThread();
    myLogListeners.add(listener);
  }

  @Override
  public void removeLogListener(@NotNull VcsLogListener listener) {
    ThreadingAssertions.assertEventDispatchThread();
    myLogListeners.remove(listener);
  }

  protected void fireFilterChangeEvent(@NotNull VisiblePack visiblePack, boolean refresh) {
    ThreadingAssertions.assertEventDispatchThread();

    for (VcsLogListener listener : myLogListeners) {
      listener.onChange(visiblePack, refresh);
    }
  }

  protected void invokeOnChange(@NotNull Runnable runnable) {
    invokeOnChange(runnable, Conditions.alwaysTrue());
  }

  protected void invokeOnChange(@NotNull Runnable runnable, @NotNull Condition<? super VcsLogDataPack> condition) {
    VcsLogUtil.invokeOnChange(this, runnable, condition);
  }

  @Override
  public void dispose() {
    ThreadingAssertions.assertEventDispatchThread();
    LOG.debug("Disposing VcsLogUi '" + myId + "'");
    myRefresher.removeVisiblePackChangeListener(myVisiblePackChangeListener);
    getTable().removeAllHighlighters();
    myVisiblePack = VisiblePack.EMPTY;
  }
}
