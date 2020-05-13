// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogRefresher;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.visible.VisiblePackRefresher;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class PostponableLogRefresher implements VcsLogRefresher {
  private static final Logger LOG = Logger.getInstance(PostponableLogRefresher.class);
  private final @NotNull VcsLogData myLogData;
  private final @NotNull Set<VirtualFile> myRootsToRefresh = new HashSet<>();
  private final @NotNull Set<VcsLogWindow> myLogWindows = new HashSet<>();

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
    LOG.assertTrue(!ContainerUtil.exists(myLogWindows, w -> w.getId().equals(window.getId())),
                   "Log window with id '" + window.getId() + "' was already added.");

    myLogWindows.add(window);
    refresherActivated(window.getRefresher(), true);
    return () -> {
      LOG.debug("Removing disposed log window " + window.toString());
      myLogWindows.remove(window);
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

  @Override
  public void refresh(final @NotNull VirtualFile root) {
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

  private void refreshPostponedRoots() {
    Set<VirtualFile> toRefresh = new HashSet<>(myRootsToRefresh);
    myRootsToRefresh.removeAll(toRefresh); // clear the set, but keep roots which could possibly arrive after collecting them in the var.
    myLogData.refresh(toRefresh);
  }

  public @NotNull Set<VcsLogWindow> getLogWindows() {
    return myLogWindows;
  }

  public static class VcsLogWindow {
    private final @NotNull String myId;
    private final @NotNull VisiblePackRefresher myRefresher;

    public VcsLogWindow(@NotNull String id, @NotNull VisiblePackRefresher refresher) {
      myId = id;
      myRefresher = refresher;
    }

    public @NotNull VisiblePackRefresher getRefresher() {
      return myRefresher;
    }

    public boolean isVisible() {
      return true;
    }

    public @NotNull String getId() {
      return myId;
    }

    @Override
    public String toString() {
      return "VcsLogWindow '" + myId + "'"; // NON-NLS
    }
  }
}
