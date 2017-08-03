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
package com.intellij.vcs.log.ui;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NamedRunnable;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
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
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.VisiblePackChangeListener;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Future;

public abstract class AbstractVcsLogUi implements VcsLogUi, Disposable {
  public static final ExtensionPointName<VcsLogHighlighterFactory> LOG_HIGHLIGHTER_FACTORY_EP =
    ExtensionPointName.create("com.intellij.logHighlighterFactory");

  @NotNull protected final Project myProject;
  @NotNull protected final VcsLogData myLogData;
  @NotNull protected final VcsLogColorManager myColorManager;
  @NotNull protected final VcsLog myLog;
  @NotNull protected final VisiblePackRefresher myRefresher;

  @NotNull protected final Collection<VcsLogListener> myLogListeners = ContainerUtil.newArrayList();
  @NotNull protected final VisiblePackChangeListener myVisiblePackChangeListener;

  @NotNull protected VisiblePack myVisiblePack;

  public AbstractVcsLogUi(@NotNull VcsLogData logData,
                          @NotNull Project project,
                          @NotNull VcsLogColorManager manager,
                          @NotNull VisiblePackRefresher refresher) {
    myProject = project;
    myLogData = logData;
    myRefresher = refresher;
    myColorManager = manager;

    Disposer.register(this, myRefresher);
    Disposer.register(logData, this);

    myLog = new VcsLogImpl(logData, this);
    myVisiblePack = VisiblePack.EMPTY;

    myVisiblePackChangeListener = visiblePack -> UIUtil.invokeLaterIfNeeded(() -> {
      if (!Disposer.isDisposed(this)) {
        setVisiblePack(visiblePack);
      }
    });
    myRefresher.addVisiblePackChangeListener(myVisiblePackChangeListener);
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

  protected abstract VcsLogFilterCollection getFilters();

  @NotNull
  public abstract VcsLogUiProperties getProperties();

  public abstract boolean isShowRootNames();

  @Override
  public boolean areGraphActionsEnabled() {
    return getTable().getRowCount() > 0;
  }

  @NotNull
  public VisiblePackRefresher getRefresher() {
    return myRefresher;
  }

  @NotNull
  public VcsLogColorManager getColorManager() {
    return myColorManager;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public VcsLog getVcsLog() {
    return myLog;
  }

  @NotNull
  public VcsLogData getLogData() {
    return myLogData;
  }

  @Override
  @NotNull
  public VisiblePack getDataPack() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myVisiblePack;
  }

  @NotNull
  public Future<Boolean> jumpToCommit(@NotNull Hash commitHash, @NotNull VirtualFile root) {
    SettableFuture<Boolean> future = SettableFuture.create();
    jumpToCommit(commitHash, root, future);
    return future;
  }

  public void jumpToCommit(@NotNull Hash commitHash, @NotNull VirtualFile root, @NotNull SettableFuture<Boolean> future) {
    jumpTo(commitHash, (model, hash) -> model.getRowOfCommit(hash, root), future);
  }

  public void jumpToCommitByPartOfHash(@NotNull String commitHash, @NotNull SettableFuture<Boolean> future) {
    jumpTo(commitHash, GraphTableModel::getRowOfCommitByPartOfHash, future);
  }

  protected <T> void jumpTo(@NotNull final T commitId,
                            @NotNull final PairFunction<GraphTableModel, T, Integer> rowGetter,
                            @NotNull final SettableFuture<Boolean> future) {
    if (future.isCancelled()) return;

    GraphTableModel model = getTable().getModel();

    int row = rowGetter.fun(model, commitId);
    if (row >= 0) {
      getTable().jumpToRow(row);
      future.set(true);
    }
    else if (model.canRequestMore()) {
      model.requestToLoadMore(() -> jumpTo(commitId, rowGetter, future));
    }
    else if (!myVisiblePack.isFull()) {
      invokeOnChange(() -> jumpTo(commitId, rowGetter, future));
    }
    else {
      handleCommitNotFound(commitId, rowGetter);
      future.set(false);
    }
  }

  protected <T> void handleCommitNotFound(@NotNull T commitId, @NotNull PairFunction<GraphTableModel, T, Integer> rowGetter) {
    VcsBalloonProblemNotifier.showOverChangesView(myProject, "Commit " + commitId.toString() + " not found.", MessageType.WARNING);
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
    Collection<VcsLogListener> logListeners = new ArrayList<>(myLogListeners);

    for (VcsLogListener listener : logListeners) {
      listener.onChange(visiblePack, refresh);
    }
  }

  public void invokeOnChange(@NotNull final Runnable runnable) {
    addLogListener(new VcsLogListener() {
      @Override
      public void onChange(@NotNull VcsLogDataPack dataPack, boolean refreshHappened) {
        runnable.run();
        removeLogListener(this);
      }
    });
  }

  @Override
  public void dispose() {
    myRefresher.removeVisiblePackChangeListener(myVisiblePackChangeListener);
    getTable().removeAllHighlighters();
    myVisiblePack = VisiblePack.EMPTY;
  }
}
