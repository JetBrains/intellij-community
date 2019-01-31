// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NamedRunnable;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.navigation.History;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.VcsLogImpl;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.VisiblePackChangeListener;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;

public abstract class AbstractVcsLogUi implements VcsLogUi, Disposable {
  private static final Logger LOG = Logger.getInstance(AbstractVcsLogUi.class);
  public static final ExtensionPointName<VcsLogHighlighterFactory> LOG_HIGHLIGHTER_FACTORY_EP =
    ExtensionPointName.create("com.intellij.logHighlighterFactory");

  @NotNull private final String myId;
  @NotNull protected final Project myProject;
  @NotNull protected final VcsLogData myLogData;
  @NotNull protected final VcsLogColorManager myColorManager;
  @NotNull protected final VcsLog myLog;
  @NotNull protected final VisiblePackRefresher myRefresher;

  @NotNull protected final Collection<VcsLogListener> myLogListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  @NotNull protected final VisiblePackChangeListener myVisiblePackChangeListener;

  @NotNull protected VisiblePack myVisiblePack;

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

    myLog = new VcsLogImpl(logData, this);
    myVisiblePack = VisiblePack.EMPTY;

    myVisiblePackChangeListener = visiblePack -> UIUtil.invokeLaterIfNeeded(() -> {
      if (!Disposer.isDisposed(this)) {
        setVisiblePack(visiblePack);
      }
    });
    myRefresher.addVisiblePackChangeListener(myVisiblePackChangeListener);
  }

  @NotNull
  @Override
  public String getId() {
    return myId;
  }

  public void requestFocus() {
    // todo fix selection
    VcsLogGraphTable graphTable = getTable();
    if (graphTable.getRowCount() > 0) {
      IdeFocusManager.getInstance(myProject).requestFocus(graphTable, true).doWhenProcessed(() -> graphTable.setRowSelectionInterval(0, 0));
    }
  }

  public void setVisiblePack(@NotNull VisiblePack pack) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    boolean permGraphChanged = myVisiblePack.getDataPack() != pack.getDataPack();

    myVisiblePack = pack;

    onVisiblePackUpdated(permGraphChanged);

    fireFilterChangeEvent(myVisiblePack, permGraphChanged);
    getTable().repaint();
  }

  protected abstract void onVisiblePackUpdated(boolean permGraphChanged);

  @NotNull
  public abstract VcsLogGraphTable getTable();

  @NotNull
  public abstract Component getMainComponent();

  @NotNull
  public abstract VcsLogUiProperties getProperties();

  @Nullable
  public abstract History getNavigationHistory();

  @Nullable
  public abstract String getHelpId();

  @NotNull
  public VisiblePackRefresher getRefresher() {
    return myRefresher;
  }

  @NotNull
  public VcsLogColorManager getColorManager() {
    return myColorManager;
  }

  @NotNull
  public VcsLog getVcsLog() {
    return myLog;
  }

  @NotNull
  public VcsLogData getLogData() {
    return myLogData;
  }

  public void requestMore(@NotNull Runnable onLoaded) {
    myRefresher.moreCommitsNeeded(onLoaded);
    getTable().setPaintBusy(true);
  }

  @Override
  @NotNull
  public VisiblePack getDataPack() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myVisiblePack;
  }

  public void jumpToRow(int row) {
    jumpTo(row, (model, r) -> {
      if (model.getRowCount() <= r) return -1;
      return r;
    }, SettableFuture.create());
  }

  @NotNull
  public ListenableFuture<Boolean> jumpToCommit(@NotNull Hash commitHash, @NotNull VirtualFile root) {
    SettableFuture<Boolean> future = SettableFuture.create();
    jumpToCommit(commitHash, root, future);
    return future;
  }

  public void jumpToCommit(@NotNull Hash commitHash, @NotNull VirtualFile root, @NotNull SettableFuture<? super Boolean> future) {
    jumpTo(commitHash, (model, hash) -> model.getRowOfCommit(hash, root), future);
  }

  public void jumpToCommitByPartOfHash(@NotNull String commitHash, @NotNull SettableFuture<? super Boolean> future) {
    if (!VcsLogUtil.HASH_REGEX.matcher(commitHash).matches()) {
      future.set(false);
      return;
    }
    jumpTo(commitHash, GraphTableModel::getRowOfCommitByPartOfHash, future);
  }

  protected <T> void jumpTo(@NotNull final T commitId,
                            @NotNull final PairFunction<GraphTableModel, T, Integer> rowGetter,
                            @NotNull final SettableFuture<? super Boolean> future) {
    if (future.isCancelled()) return;

    GraphTableModel model = getTable().getModel();

    int result = rowGetter.fun(model, commitId);
    if (result >= 0) {
      getTable().jumpToRow(result);
      future.set(true);
    }
    else if (model.canRequestMore()) {
      model.requestToLoadMore(() -> jumpTo(commitId, rowGetter, future));
    }
    else if (!myVisiblePack.isFull()) {
      invokeOnChange(() -> jumpTo(commitId, rowGetter, future));
    }
    else {
      handleCommitNotFound(commitId, result == GraphTableModel.COMMIT_DOES_NOT_MATCH, rowGetter);
      future.set(false);
    }
  }

  protected <T> void handleCommitNotFound(@NotNull T commitId,
                                          boolean commitExists,
                                          @NotNull PairFunction<GraphTableModel, T, Integer> rowGetter) {
    String message = getCommitNotFoundMessage(commitId, commitExists);
    VcsBalloonProblemNotifier.showOverChangesView(myProject, message, MessageType.WARNING);
  }

  @NotNull
  protected static <T> String getCommitNotFoundMessage(@NotNull T commitId, boolean exists) {
    return exists ? "Commit " + getCommitPresentation(commitId) + " doesn't match the filters" :
           "Commit " + getCommitPresentation(commitId) + " not found";
  }

  @NotNull
  protected static <T> String getCommitPresentation(@NotNull T commitId) {
    if (commitId instanceof Hash) {
      return ((Hash)commitId).toShortString();
    }
    else if (commitId instanceof String) {
      return VcsLogUtil.getShortHash((String)commitId);
    }
    return commitId.toString();
  }

  protected void showWarningWithLink(@NotNull String mainText, @NotNull String linkText, @NotNull Runnable onClick) {
    VcsBalloonProblemNotifier.showOverChangesView(myProject, mainText, MessageType.WARNING,
                                                  new NamedRunnable(linkText) {
                                                    @Override
                                                    public void run() {
                                                      onClick.run();
                                                    }
                                                  });
  }

  @Override
  public void addLogListener(@NotNull VcsLogListener listener) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myLogListeners.add(listener);
  }

  @Override
  public void removeLogListener(@NotNull VcsLogListener listener) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myLogListeners.remove(listener);
  }

  protected void fireFilterChangeEvent(@NotNull VisiblePack visiblePack, boolean refresh) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    for (VcsLogListener listener : myLogListeners) {
      listener.onChange(visiblePack, refresh);
    }
  }

  public void invokeOnChange(@NotNull Runnable runnable) {
    invokeOnChange(runnable, Conditions.alwaysTrue());
  }

  protected void invokeOnChange(@NotNull Runnable runnable, @NotNull Condition<? super VcsLogDataPack> condition) {
    addLogListener(new VcsLogListener() {
      @Override
      public void onChange(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
        if (condition.value(dataPack)) {
          runnable.run();
          removeLogListener(this);
        }
      }
    });
  }

  @Override
  public void dispose() {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    LOG.debug("Disposing VcsLogUi \'" + myId + "\'");
    myRefresher.removeVisiblePackChangeListener(myVisiblePackChangeListener);
    getTable().removeAllHighlighters();
    myVisiblePack = VisiblePack.EMPTY;
  }
}
