// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsLogUiEx;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class PostponableLogRefresher {
  private static final Logger LOG = Logger.getInstance(PostponableLogRefresher.class);
  private final @NotNull VcsLogData myLogData;
  private final @NotNull Set<VirtualFile> myRootsToRefresh = new HashSet<>();
  private final @NotNull Set<VcsLogWindow> myLogWindows = new HashSet<>();
  private final @NotNull Map<String, Throwable> myCreationTraces = new HashMap<>();

  public PostponableLogRefresher(@NotNull VcsLogData logData) {
    myLogData = logData;
    myLogData.addDataPackChangeListener(dataPack -> {
      LOG.debug("Refreshing log windows " + myLogWindows);
      for (VcsLogWindow window : myLogWindows) {
        dataPackArrived(window.getRefresher(), window.isVisible());
      }
    });
  }

  public @NotNull Disposable addLogWindow(@NotNull VcsLogWindow window) {
    String windowId = window.getId();
    if (ContainerUtil.exists(myLogWindows, w -> w.getId().equals(windowId))) {
      throw new CannotAddVcsLogWindowException("Log window with id '" + windowId + "' was already added. " +
                                               "Existing windows:\n" + getLogWindowsInformation(),
                                               myCreationTraces.get(windowId));
    }

    myLogWindows.add(window);
    myCreationTraces.put(windowId, new Throwable("Creation trace for " + window));
    refresherActivated(window.getRefresher(), true);
    return () -> {
      LOG.debug("Removing disposed log window " + window);
      myLogWindows.remove(window);
      myCreationTraces.remove(windowId);
    };
  }

  public static boolean keepUpToDate() {
    return Registry.is("vcs.log.keep.up.to.date") && !PowerSaveMode.isEnabled();
  }

  private boolean canRefreshNow() {
    if (keepUpToDate()) return true;
    return isLogVisible();
  }

  public boolean isLogVisible() {
    for (VcsLogWindow window : myLogWindows) {
      if (window.isVisible()) return true;
    }
    return false;
  }

  public void refresherActivated(@NotNull VisiblePackRefresher refresher, boolean firstTime) {
    myLogData.initialize();

    if (!myRootsToRefresh.isEmpty()) {
      refreshPostponedRoots();
    }
    else {
      refresher.setValid(true, firstTime);
    }
  }

  private static void dataPackArrived(@NotNull VisiblePackRefresher refresher, boolean visible) {
    refresher.setValid(visible, true);
  }

  @RequiresEdt
  public void refresh(@NotNull VirtualFile root) {
    if (canRefreshNow()) {
      myLogData.refresh(Collections.singleton(root));
    }
    else {
      LOG.debug("Postponed refresh for " + root);
      myRootsToRefresh.add(root);
    }
  }

  @RequiresEdt
  public boolean hasPostponedRoots() {
    return !myRootsToRefresh.isEmpty();
  }

  @RequiresEdt
  public void refreshPostponedRoots() {
    Set<VirtualFile> toRefresh = new HashSet<>(myRootsToRefresh);
    myRootsToRefresh.removeAll(toRefresh); // clear the set, but keep roots which could possibly arrive after collecting them in the var.
    myLogData.refresh(toRefresh);
  }

  public @NotNull Set<VcsLogWindow> getLogWindows() {
    return myLogWindows;
  }

  public @NotNull String getLogWindowsInformation() {
    return StringUtil.join(myLogWindows, window -> {
      String isVisible = window.isVisible() ? " (visible)" : "";
      String isDisposed = Disposer.isDisposed(window.getRefresher()) ? " (disposed)" : "";
      return window + isVisible + isDisposed;
    }, "\n");
  }

  public static class VcsLogWindow {
    private final @NotNull VcsLogUiEx myUi;

    public VcsLogWindow(@NotNull VcsLogUiEx ui) {
      myUi = ui;
    }

    public @NotNull VcsLogUiEx getUi() {
      return myUi;
    }

    public @NotNull VisiblePackRefresher getRefresher() {
      return myUi.getRefresher();
    }

    public boolean isVisible() {
      return true;
    }

    public @NotNull String getId() {
      return myUi.getId();
    }

    @Override
    @NonNls
    public String toString() {
      return "VcsLogWindow '" + myUi.getId() + "'";
    }
  }
}
