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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogRefresher;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.DataPackChangeListener;
import com.intellij.vcs.log.data.VcsLogDataManager;
import com.intellij.vcs.log.data.VcsLogFilterer;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PostponableLogRefresher implements VcsLogRefresher {
  @NotNull protected final VcsLogDataManager myDataManager;
  @NotNull private final Set<VirtualFile> myRootsToRefresh = ContainerUtil.newHashSet();
  @NotNull private final Set<VcsLogWindow> myLogWindows = ContainerUtil.newHashSet();

  public PostponableLogRefresher(@NotNull VcsLogDataManager dataManager) {
    myDataManager = dataManager;
    myDataManager.addDataPackChangeListener(new DataPackChangeListener() {
      @Override
      public void onDataPackChange(@NotNull DataPack dataPack) {
        for (VcsLogWindow window : myLogWindows) {
          dataPackArrived(window.getFilterer(), window.isVisible());
        }
      }
    });
  }

  @NotNull
  public Disposable addLogWindow(@NotNull VcsLogWindow window) {
    myLogWindows.add(window);
    filtererActivated(window.getFilterer(), true);
    return new Disposable() {
      @Override
      public void dispose() {
        myLogWindows.remove(window);
      }
    };
  }

  @NotNull
  public Disposable addLogWindow(@NotNull VcsLogFilterer filterer) {
    return addLogWindow(new VcsLogWindow(filterer));
  }

  public static boolean keepUpToDate() {
    return Registry.is("vcs.log.keep.up.to.date");
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

  public void filtererActivated(@NotNull VcsLogFilterer filterer, boolean firstTime) {
    if (!myRootsToRefresh.isEmpty()) {
      refreshPostponedRoots();
    }
    else {
      if (!filterer.isValid() || firstTime) {
        filterer.onRefresh();
      }
    }
  }

  private static void dataPackArrived(@NotNull VcsLogFilterer filterer, boolean canRefreshNow) {
    if (canRefreshNow) {
      filterer.onRefresh();
    }
    else {
      filterer.invalidate();
    }
  }

  public void refresh(@NotNull final VirtualFile root) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (canRefreshNow()) {
          myDataManager.refresh(Collections.singleton(root));
        }
        else {
          myRootsToRefresh.add(root);
        }
      }
    }, ModalityState.any());
  }

  protected void refreshPostponedRoots() {
    Set<VirtualFile> toRefresh = new HashSet<VirtualFile>(myRootsToRefresh);
    myRootsToRefresh.removeAll(toRefresh); // clear the set, but keep roots which could possibly arrive after collecting them in the var.
    myDataManager.refresh(toRefresh);
  }

  @NotNull
  public Set<VcsLogWindow> getLogWindows() {
    return myLogWindows;
  }

  public static class VcsLogWindow {
    @NotNull private final VcsLogFilterer myFilterer;

    public VcsLogWindow(@NotNull VcsLogFilterer filterer) {
      myFilterer = filterer;
    }

    @NotNull
    public VcsLogFilterer getFilterer() {
      return myFilterer;
    }

    public boolean isVisible() {
      return true;
    }
  }
}
