// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui;

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
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.VcsLogImpl;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.VisiblePackChangeListener;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class AbstractVcsLogUi implements VcsLogUiEx, Disposable {
  private static final Logger LOG = Logger.getInstance(AbstractVcsLogUi.class);
  public static final ExtensionPointName<VcsLogHighlighterFactory> LOG_HIGHLIGHTER_FACTORY_EP =
    ExtensionPointName.create("com.intellij.logHighlighterFactory");

  @NotNull private final String myId;
  @NotNull protected final Project myProject;
  @NotNull protected final VcsLogData myLogData;
  @NotNull protected final VcsLogColorManager myColorManager;
  @NotNull protected final VcsLogImpl myLog;
  @NotNull protected final VisiblePackRefresher myRefresher;

  @NotNull protected final Collection<VcsLogListener> myLogListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  @NotNull protected final VisiblePackChangeListener myVisiblePackChangeListener;

  @NotNull protected volatile VisiblePack myVisiblePack = VisiblePack.EMPTY;

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

  public void setVisiblePack(@NotNull VisiblePack pack) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    boolean permGraphChanged = myVisiblePack.getDataPack() != pack.getDataPack();

    myVisiblePack = pack;

    onVisiblePackUpdated(permGraphChanged);

    fireFilterChangeEvent(myVisiblePack, permGraphChanged);
    getTable().repaint();
  }

  protected abstract void onVisiblePackUpdated(boolean permGraphChanged);

  @Override
  @NotNull
  public VisiblePackRefresher getRefresher() {
    return myRefresher;
  }

  @Override
  @NotNull
  public VcsLogColorManager getColorManager() {
    return myColorManager;
  }

  @Override
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
    return myVisiblePack;
  }

  @Override
  public <T> void jumpTo(@NotNull T commitId,
                         @NotNull PairFunction<? super VisiblePack, ? super T, Integer> rowGetter,
                         @NotNull SettableFuture<? super Boolean> future,
                         boolean silently,
                         boolean focus) {
    if (future.isCancelled()) return;

    GraphTableModel model = getTable().getModel();

    int result = rowGetter.fun(myVisiblePack, commitId);
    if (result >= 0) {
      getTable().jumpToRow(result, focus);
      future.set(true);
    }
    else if (model.canRequestMore()) {
      model.requestToLoadMore(() -> jumpTo(commitId, rowGetter, future, silently, focus));
    }
    else if (!myVisiblePack.isFull()) {
      invokeOnChange(() -> jumpTo(commitId, rowGetter, future, silently, focus));
    }
    else {
      if (!silently) handleCommitNotFound(commitId, result == GraphTableModel.COMMIT_DOES_NOT_MATCH, rowGetter);
      future.set(false);
    }
  }

  protected <T> void handleCommitNotFound(@NotNull T commitId,
                                          boolean commitExists,
                                          @NotNull PairFunction<? super VisiblePack, ? super T, Integer> rowGetter) {
    String message = getCommitNotFoundMessage(commitId, commitExists);
    VcsBalloonProblemNotifier.showOverChangesView(myProject, message, MessageType.WARNING);
  }

  @NotNull
  @Nls
  protected static <T> String getCommitNotFoundMessage(@NotNull T commitId, boolean exists) {
    return exists ? VcsLogBundle.message("vcs.log.commit.does.not.match", getCommitPresentation(commitId)) :
           VcsLogBundle.message("vcs.log.commit.not.found", getCommitPresentation(commitId));
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

  protected void showWarningWithLink(@Nls @NotNull String mainText, @Nls @NotNull String linkText, @NotNull Runnable onClick) {
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

  protected void invokeOnChange(@NotNull Runnable runnable) {
    invokeOnChange(runnable, Conditions.alwaysTrue());
  }

  protected void invokeOnChange(@NotNull Runnable runnable, @NotNull Condition<? super VcsLogDataPack> condition) {
    VcsLogUtil.invokeOnChange(this, runnable, condition);
  }

  @Override
  public void dispose() {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    LOG.debug("Disposing VcsLogUi '" + myId + "'");
    myRefresher.removeVisiblePackChangeListener(myVisiblePackChangeListener);
    getTable().removeAllHighlighters();
    myVisiblePack = VisiblePack.EMPTY;
  }
}
