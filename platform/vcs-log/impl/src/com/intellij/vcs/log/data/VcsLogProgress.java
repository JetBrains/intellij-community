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
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class VcsLogProgress {
  @NotNull private final Object myLock = new Object();
  @NotNull private final List<ProgressListener> myListeners = ContainerUtil.newArrayList();
  private int myRunningTasksCount = 0;

  @NotNull
  public ProgressIndicator createProgressIndicator(@NotNull Task.Backgroundable task) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return new EmptyProgressIndicator();
    }
    else if (showProgressInLog()) {
      return new VcsLogProgressIndicator();
    }
    else {
      return new BackgroundableProcessIndicator(task);
    }
  }

  public void addProgressIndicatorListener(@NotNull ProgressListener listener, @Nullable Disposable parentDisposable) {
    synchronized (myLock) {
      myListeners.add(listener);
      if (parentDisposable != null) {
        Disposer.register(parentDisposable, () -> removeProgressIndicatorListener(listener));
      }
      if (isRunning()) ApplicationManager.getApplication().invokeLater(listener::progressStarted);
    }
  }

  public void removeProgressIndicatorListener(@NotNull ProgressListener listener) {
    synchronized (myLock) {
      myListeners.remove(listener);
    }
  }

  public boolean isRunning() {
    synchronized (myLock) {
      return myRunningTasksCount > 0;
    }
  }

  private void started() {
    synchronized (myLock) {
      myRunningTasksCount++;
      if (myRunningTasksCount == 1) fireNotification(ProgressListener::progressStarted);
    }
  }

  private void stopped() {
    synchronized (myLock) {
      myRunningTasksCount--;
      if (myRunningTasksCount == 0) fireNotification(ProgressListener::progressStopped);
    }
  }

  private void fireNotification(@NotNull Consumer<ProgressListener> action) {
    synchronized (myLock) {
      List<ProgressListener> list = ContainerUtil.newArrayList(myListeners);
      ApplicationManager.getApplication().invokeLater(() -> list.forEach(action));
    }
  }

  public boolean showProgressInLog() {
    return Registry.is("vcs.log.keep.up.to.date");
  }

  private class VcsLogProgressIndicator extends AbstractProgressIndicatorBase {
    @Override
    public synchronized void start() {
      super.start();
      started();
    }

    @Override
    public synchronized void stop() {
      super.stop();
      stopped();
    }
  }

  public interface ProgressListener {
    void progressStarted();

    void progressStopped();
  }
}
