// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ConcurrentList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class SymlinkRegistry {

  public static final SymlinkRegistry INSTANCE = new SymlinkRegistry();

  private final ConcurrentList<SymlinkListener> myListeners = ContainerUtil.createConcurrentList();
  // TODO make persistent across sessions
  private final Set<Integer> mySymlinks = new HashSet<>();

  private final SymlinkEventsBatchProducer myBatchProducer = new SymlinkEventsBatchProducer();

  void symlinkTargetStored(int fileId) {
    SymlinkEventType eventType;
    synchronized (mySymlinks) {
      if (mySymlinks.add(fileId)) {
        eventType = SymlinkEventType.ADDED;
      }
      else {
        eventType = SymlinkEventType.UPDATED;
      }
    }
    myBatchProducer.symlinkEvent(eventType, fileId);
  }

  void symlinkPossiblyRemoved(int fileId) {
    synchronized (mySymlinks) {
      if (!mySymlinks.remove(fileId)) {
        return;
      }
    }
    myBatchProducer.symlinkEvent(SymlinkEventType.DELETED, fileId);
  }

  private void notifyListeners(List<SymlinkEvent> events) {
    for (SymlinkListener listener : myListeners) {
      listener.symlinkEvents(events);
    }
  }

  public void watchSymlinks(@NotNull SymlinkListener listener, @Nullable Disposable parentDisposable) {
    myBatchProducer.addAndNotifyNewListener(listener, parentDisposable);
  }

  public interface SymlinkListener {
    void symlinkEvents(@NotNull List<SymlinkEvent> events);
  }

  public enum SymlinkEventType {
    ADDED,
    DELETED,
    UPDATED
  }

  public static class SymlinkEvent {
    public final SymlinkEventType eventType;
    public final int fileId;

    SymlinkEvent(SymlinkEventType type, int fileId) {
      eventType = type;
      this.fileId = fileId;
    }

    @Override
    public String toString() {
      return "SymlinkEvent{" +
             "eventType=" + eventType +
             ", fileId=" + fileId +
             '}';
    }
  }

  private class SymlinkEventsBatchProducer {

    private final ExecutorService mySymlinkEventExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("WatchRootsMap", 1);
    private final AtomicReference<Future<?>> myLastSymlinkEventsTask = new AtomicReference<>();
    private final List<SymlinkEvent> mySymlinkEventsQueue = new ArrayList<>();

    private SymlinkEventsBatchProducer() {
    }

    private void symlinkEvent(SymlinkRegistry.SymlinkEventType eventType, int fileId) {
      synchronized (mySymlinkEventsQueue) {
        mySymlinkEventsQueue.add(new SymlinkEvent(eventType, fileId));
      }
      Future<?> lastTask = myLastSymlinkEventsTask.getAndSet(
        mySymlinkEventExecutor.submit(this::processSymlinkEvents));
      if (lastTask != null) {
        lastTask.cancel(false);
      }
    }

    private void processSymlinkEvents() {
      List<SymlinkEvent> events;
      synchronized (mySymlinkEventsQueue) {
        events = new ArrayList<>(mySymlinkEventsQueue);
        mySymlinkEventsQueue.clear();
      }
      notifyListeners(events);
    }

    public void addAndNotifyNewListener(@NotNull SymlinkListener listener, @Nullable Disposable parentDisposable) {
      mySymlinkEventExecutor.execute(() -> {
        processSymlinkEvents();
        List<SymlinkEvent> events;
        synchronized (mySymlinks) {
          events = ContainerUtil.map(
            mySymlinks, fileId -> new SymlinkEvent(SymlinkEventType.ADDED, fileId));
        }

        if (!myListeners.contains(listener)) {
          myListeners.add(listener);
          if (parentDisposable != null) {
            Disposer.register(parentDisposable, () -> {
              myListeners.remove(listener);
            });
          }
          listener.symlinkEvents(events);
        }
      });
    }
  }
}
