// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui;

import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.PairFunction;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogListener;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.VcsLogImpl;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * A base class to simplify {@link VcsLogUiEx} implementation
 */
public abstract class VcsLogUiBase implements VcsLogUiEx {
  private final @NotNull String myId;
  protected final @NotNull VcsLogData myLogData;
  protected final @NotNull VcsLogImpl myLog;
  protected final @NotNull VisiblePackRefresher myRefresher;
  protected final @NotNull CheckedDisposable myDisposableFlag = Disposer.newCheckedDisposable();

  protected final @NotNull Collection<VcsLogListener> myLogListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public VcsLogUiBase(@NotNull String id, @NotNull VcsLogData logData, @NotNull VisiblePackRefresher refresher) {
    myId = id;
    myLogData = logData;
    myRefresher = refresher;

    Disposer.register(this, myRefresher);
    Disposer.register(this, myDisposableFlag);

    myLog = new VcsLogImpl(logData, this);
  }

  @Override
  public @NotNull String getId() {
    return myId;
  }

  @Override
  public @NotNull VisiblePackRefresher getRefresher() {
    return myRefresher;
  }

  @Override
  public @NotNull VcsLog getVcsLog() {
    return myLog;
  }

  @Override
  public @NotNull VcsLogData getLogData() {
    return myLogData;
  }

  @RequiresEdt
  @Override
  public void addLogListener(@NotNull VcsLogListener listener) {
    myLogListeners.add(listener);
  }

  @RequiresEdt
  @Override
  public void removeLogListener(@NotNull VcsLogListener listener) {
    myLogListeners.remove(listener);
  }

  @RequiresEdt
  protected void fireChangeEvent(@NotNull VisiblePack visiblePack, boolean refresh) {
    for (VcsLogListener listener : myLogListeners) {
      listener.onChange(visiblePack, refresh);
    }
  }

  @Override
  public <T> void jumpTo(@NotNull T commitId,
                         @NotNull PairFunction<? super VisiblePack, ? super T, Integer> rowGetter,
                         @NotNull SettableFuture<JumpResult> future,
                         boolean silently,
                         boolean focus) {
  }

  @Override
  public void dispose() {
  }
}
