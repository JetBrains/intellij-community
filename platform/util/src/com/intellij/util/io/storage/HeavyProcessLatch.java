// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.storage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Deque;
import java.util.EventListener;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class HeavyProcessLatch {
  private static final Logger LOG = Logger.getInstance(HeavyProcessLatch.class);
  public static final HeavyProcessLatch INSTANCE = new HeavyProcessLatch();

  private final Set<String> myHeavyProcesses = ContainerUtil.newConcurrentSet();
  private final EventDispatcher<HeavyProcessListener> myEventDispatcher = EventDispatcher.create(HeavyProcessListener.class);

  private final Deque<Runnable> toExecuteOutOfHeavyActivity = new ConcurrentLinkedDeque<>();

  private HeavyProcessLatch() {
  }

  @NotNull
  public AccessToken processStarted(@NotNull final String operationName) {
    myHeavyProcesses.add(operationName);
    myEventDispatcher.getMulticaster().processStarted();
    return new AccessToken() {
      @Override
      public void finish() {
        processFinished(operationName);
      }
    };
  }

  private void processFinished(@NotNull String operationName) {
    myHeavyProcesses.remove(operationName);
    myEventDispatcher.getMulticaster().processFinished();
    if (isRunning()) {
      return;
    }

    Runnable runnable;
    while ((runnable = toExecuteOutOfHeavyActivity.pollFirst()) != null) {
      try {
        runnable.run();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  public boolean isRunning() {
    return !myHeavyProcesses.isEmpty();
  }

  public String getRunningOperationName() {
    if (myHeavyProcesses.isEmpty()) {
      return null;
    }
    else {
      Iterator<String> iterator = myHeavyProcesses.iterator();
      return iterator.hasNext() ? iterator.next() : null;
    }
  }

  public interface HeavyProcessListener extends EventListener {
    default void processStarted() {
    }

    void processFinished();
  }

  public void addListener(@NotNull HeavyProcessListener listener,
                          @NotNull Disposable parentDisposable) {
    myEventDispatcher.addListener(listener, parentDisposable);
  }

  public void executeOutOfHeavyProcess(@NotNull Runnable runnable) {
    if (isRunning()) {
      toExecuteOutOfHeavyActivity.add(runnable);
    }
    else {
      runnable.run();
    }
  }
}