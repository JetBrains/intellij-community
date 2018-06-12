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
package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class VcsLogProgress implements Disposable {
  @NotNull private final Object myLock = new Object();
  @NotNull private final List<ProgressListener> myListeners = ContainerUtil.newArrayList();
  @NotNull private final Set<VcsLogProgressIndicator> myTasksWithVisibleProgress = ContainerUtil.newHashSet();
  @NotNull private final Set<ProgressIndicator> myTasksWithSilentProgress = ContainerUtil.newHashSet();
  private boolean myDisposed = false;

  public VcsLogProgress(@NotNull Project project, @NotNull Disposable parent) {
    Disposer.register(parent, () -> Disposer.dispose(this));
    Disposer.register(project, this);
  }

  @NotNull
  public ProgressIndicator createProgressIndicator(@NotNull ProgressKey key) {
    return createProgressIndicator(true, key);
  }

  @NotNull
  public ProgressIndicator createProgressIndicator(boolean visible, @NotNull ProgressKey key) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return new EmptyProgressIndicator();
    }
    return new VcsLogProgressIndicator(visible, key);
  }

  public void addProgressIndicatorListener(@NotNull ProgressListener listener, @Nullable Disposable parentDisposable) {
    synchronized (myLock) {
      myListeners.add(listener);
      if (parentDisposable != null) {
        Disposer.register(parentDisposable, () -> removeProgressIndicatorListener(listener));
      }
      if (isRunning()) {
        Set<ProgressKey> keys = getRunningKeys();
        ApplicationManager.getApplication().invokeLater(() -> listener.progressStarted(keys));
      }
    }
  }

  public void removeProgressIndicatorListener(@NotNull ProgressListener listener) {
    synchronized (myLock) {
      myListeners.remove(listener);
    }
  }

  public boolean isRunning() {
    synchronized (myLock) {
      return !myTasksWithVisibleProgress.isEmpty();
    }
  }

  @NotNull
  public Set<ProgressKey> getRunningKeys() {
    synchronized (myLock) {
      return ContainerUtil.map2Set(myTasksWithVisibleProgress, VcsLogProgressIndicator::getKey);
    }
  }

  private void started(@NotNull VcsLogProgressIndicator indicator) {
    synchronized (myLock) {
      if (myDisposed) {
        indicator.cancel();
        return;
      }
      if (indicator.isVisible()) {
        Set<ProgressKey> oldKeys = getRunningKeys();
        myTasksWithVisibleProgress.add(indicator);
        if (myTasksWithVisibleProgress.size() == 1) {
          ProgressKey key = indicator.getKey();
          fireNotification(listener -> listener.progressStarted(Collections.singleton(key)));
        }
        else {
          keysUpdated(oldKeys);
        }
      }
      else {
        myTasksWithSilentProgress.add(indicator);
      }
    }
  }

  private void stopped(@NotNull VcsLogProgressIndicator indicator) {
    synchronized (myLock) {
      if (indicator.isVisible()) {
        myTasksWithVisibleProgress.remove(indicator);
        if (myTasksWithVisibleProgress.isEmpty()) fireNotification(ProgressListener::progressStopped);
      }
      else {
        myTasksWithSilentProgress.remove(indicator);
      }
    }
  }

  private void keysUpdated(@NotNull Set<ProgressKey> oldKeys) {
    synchronized (myLock) {
      Set<ProgressKey> newKeys = getRunningKeys();
      if (!oldKeys.equals(newKeys)) {
        fireNotification(listener -> listener.progressChanged(newKeys));
      }
    }
  }

  private void fireNotification(@NotNull Consumer<ProgressListener> action) {
    synchronized (myLock) {
      List<ProgressListener> list = ContainerUtil.newArrayList(myListeners);
      ApplicationManager.getApplication().invokeLater(() -> list.forEach(action));
    }
  }

  @Override
  public void dispose() {
    synchronized (myLock) {
      myDisposed = true;
      for (ProgressIndicator indicator : myTasksWithVisibleProgress) {
        indicator.cancel();
      }
      for (ProgressIndicator indicator : myTasksWithSilentProgress) {
        indicator.cancel();
      }
    }
  }

  private class VcsLogProgressIndicator extends AbstractProgressIndicatorBase {
    @NotNull private ProgressKey myKey;
    private final boolean myVisible;

    private VcsLogProgressIndicator(boolean visible, @NotNull ProgressKey key) {
      myKey = key;
      myVisible = visible;
    }

    @Override
    public synchronized void start() {
      super.start();
      started(this);
    }

    @Override
    public synchronized void stop() {
      super.stop();
      stopped(this);
    }

    public void updateKey(@NotNull ProgressKey key) {
      synchronized (myLock) {
        Set<ProgressKey> oldKeys = getRunningKeys();
        myKey = key;
        keysUpdated(oldKeys);
      }
    }

    public boolean isVisible() {
      return myVisible;
    }

    @NotNull
    public ProgressKey getKey() {
      synchronized (myLock) {
        return myKey;
      }
    }
  }

  public interface ProgressListener {
    void progressStarted(@NotNull Collection<ProgressKey> keys);

    void progressChanged(@NotNull Collection<ProgressKey> keys);

    void progressStopped();
  }

  public static class ProgressKey {
    @NotNull private final String myName;

    public ProgressKey(@NotNull String name) {
      myName = name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ProgressKey key = (ProgressKey)o;
      return Objects.equals(myName, key.myName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myName);
    }
  }

  public static void updateCurrentKey(@NotNull ProgressKey key) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator instanceof VcsLogProgressIndicator) {
      ((VcsLogProgressIndicator)indicator).updateKey(key);
    }
  }
}
