// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.StandardProgressIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class VcsLogProgress implements Disposable {
  private final @NotNull CheckedDisposable myDisposableFlag = Disposer.newCheckedDisposable();
  private final @NotNull Object myLock = new Object();
  private final @NotNull List<ProgressListener> myListeners = new ArrayList<>();
  private final @NotNull Set<VcsLogProgressIndicator> myTasksWithVisibleProgress = new HashSet<>();
  private final @NotNull Set<ProgressIndicator> myTasksWithSilentProgress = new HashSet<>();
  private boolean myDisposed = false;

  public VcsLogProgress(@NotNull Disposable parent) {
    Disposer.register(parent, this);
    Disposer.register(this, myDisposableFlag);
  }

  public @NotNull ProgressIndicator createProgressIndicator(@NotNull ProgressKey key) {
    return createProgressIndicator(true, key);
  }

  public @NotNull ProgressIndicator createProgressIndicator(boolean visible, @NotNull ProgressKey key) {
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

  public @NotNull Set<ProgressKey> getRunningKeys() {
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

  private void fireNotification(@NotNull Consumer<? super ProgressListener> action) {
    synchronized (myLock) {
      List<ProgressListener> list = new ArrayList<>(myListeners);
      ApplicationManager.getApplication().invokeLater(() -> list.forEach(action), o -> myDisposableFlag.isDisposed());
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

  private final class VcsLogProgressIndicator extends AbstractProgressIndicatorBase implements StandardProgressIndicator {
    private @NotNull ProgressKey myKey;
    private final boolean myVisible;

    private VcsLogProgressIndicator(boolean visible, @NotNull ProgressKey key) {
      myKey = key;
      myVisible = visible;
      if (!visible) dontStartActivity();
    }

    @Override
    public void start() {
      synchronized (getLock()) {
        super.start();
        started(this);
      }
    }

    @Override
    public void stop() {
      synchronized (getLock()) {
        super.stop();
        stopped(this);
      }
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

    public @NotNull ProgressKey getKey() {
      synchronized (myLock) {
        return myKey;
      }
    }
  }

  public interface ProgressListener {
    void progressStarted(@NotNull Collection<? extends ProgressKey> keys);

    void progressChanged(@NotNull Collection<? extends ProgressKey> keys);

    void progressStopped();
  }

  public static class ProgressKey {
    private final @NotNull String myName;

    public ProgressKey(@NonNls @NotNull String name) {
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
