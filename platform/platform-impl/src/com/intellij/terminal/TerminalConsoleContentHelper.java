// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

class TerminalConsoleContentHelper implements Disposable {

  private static final int FLUSH_TIMEOUT = 200;

  private final Collection<ObservableConsoleView.ChangeListener> myChangeListeners = new CopyOnWriteArraySet<>();
  private final Set<ConsoleViewContentType> myContentTypes = ContainerUtil.newConcurrentSet();
  private final Alarm myAlarm;
  private final AtomicBoolean myRequested = new AtomicBoolean(false);
  private volatile boolean myDisposed = false;

  TerminalConsoleContentHelper(@NotNull TerminalExecutionConsole console) {
    myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD /* won't be disposed to call addRequest safely */);
    Disposer.register(console, this);
  }

  public void addChangeListener(@NotNull ObservableConsoleView.ChangeListener listener, @NotNull Disposable parent) {
    myChangeListeners.add(listener);
    Disposer.register(parent, () -> myChangeListeners.remove(listener));
  }

  public void onContentTypePrinted(@NotNull ConsoleViewContentType contentType) {
    myContentTypes.add(contentType);
    if (myRequested.compareAndSet(false, true) && !myDisposed) {
      myAlarm.addRequest(this::flush, FLUSH_TIMEOUT);
    }
  }

  private void flush() {
    if (myDisposed) return;
    myRequested.set(false);
    List<ConsoleViewContentType> contentTypes = new ArrayList<>(myContentTypes);
    myContentTypes.removeAll(contentTypes);
    if (!contentTypes.isEmpty()) {
      fireContentAdded(contentTypes);
    }
  }

  private void fireContentAdded(@NotNull List<ConsoleViewContentType> contentTypes) {
    for (ObservableConsoleView.ChangeListener listener : myChangeListeners) {
      listener.contentAdded(contentTypes);
    }
  }

  @Override
  public void dispose() {
    myDisposed = true;
    myAlarm.cancelAllRequests();
  }
}
