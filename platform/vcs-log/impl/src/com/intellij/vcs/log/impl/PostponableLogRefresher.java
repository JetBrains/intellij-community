// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsLogRefresher;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PostponableLogRefresher implements VcsLogRefresher {
  private static final Logger LOG = Logger.getInstance(PostponableLogRefresher.class);
  @NotNull protected final VcsLogData myLogData;
  @NotNull private final Set<VirtualFile> myRootsToRefresh = new HashSet<>();
  @NotNull private final Set<VcsLogWindow> myLogWindows = new HashSet<>();

  public PostponableLogRefresher(@NotNull VcsLogData logData) {
    myLogData = logData;
    myLogData.addDataPackChangeListener(dataPack -> {
      LOG.debug("Refreshing log windows " + myLogWindows);
      for (VcsLogWindow window : myLogWindows) {
        dataPackArrived(window.getRefresher(), window.isVisible());
      }
    });
  }

  @NotNull
  public Disposable addLogWindow(@NotNull VcsLogWindow window) {
    myLogWindows.add(window);
    refresherActivated(window.getRefresher(), true);
    return () -> {
      LOG.debug("Removing disposed log window " + window.toString());
      myLogWindows.remove(window);
    };
  }

  @NotNull
  public Disposable addLogWindow(@NotNull VisiblePackRefresher refresher) {
    return addLogWindow(new VcsLogWindow(refresher));
  }

  public static boolean keepUpToDate() {
    return Registry.is("vcs.log.keep.up.to.date") && !PowerSaveMode.isEnabled();
  }

  protected boolean canRefreshNow() {
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
    if (!visible) {
      refresher.setValid(false, true);
    }
    else {
      refresher.onRefresh();
    }
  }

  @Override
  public void refresh(@NotNull final VirtualFile root) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (canRefreshNow()) {
        myLogData.refresh(Collections.singleton(root));
      }
      else {
        LOG.debug("Postponed refresh for " + root);
        myRootsToRefresh.add(root);
      }
    }, ModalityState.any());
  }

  protected void refreshPostponedRoots() {
    Set<VirtualFile> toRefresh = new HashSet<>(myRootsToRefresh);
    myRootsToRefresh.removeAll(toRefresh); // clear the set, but keep roots which could possibly arrive after collecting them in the var.
    myLogData.refresh(toRefresh);
  }

  @NotNull
  public Set<VcsLogWindow> getLogWindows() {
    return myLogWindows;
  }

  public static class VcsLogWindow {
    @NotNull private final VisiblePackRefresher myRefresher;

    public VcsLogWindow(@NotNull VisiblePackRefresher refresher) {
      myRefresher = refresher;
    }

    @NotNull
    public VisiblePackRefresher getRefresher() {
      return myRefresher;
    }

    public boolean isVisible() {
      return true;
    }

    @Override
    public String toString() {
      return "VcsLogWindow '" + myRefresher + "'";
    }
  }
}
