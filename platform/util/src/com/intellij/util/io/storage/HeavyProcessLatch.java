// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.storage;

import com.intellij.UtilBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Deque;
import java.util.EventListener;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class HeavyProcessLatch {
  private static final Logger LOG = Logger.getInstance(HeavyProcessLatch.class);
  public static final HeavyProcessLatch INSTANCE = new HeavyProcessLatch();

  private final Map<String, Type> myHeavyProcesses = ContainerUtil.newConcurrentMap();
  private final EventDispatcher<HeavyProcessListener> myEventDispatcher = EventDispatcher.create(HeavyProcessListener.class);

  private final Deque<Runnable> toExecuteOutOfHeavyActivity = new ConcurrentLinkedDeque<>();

  private HeavyProcessLatch() {
  }

  /**
   * Approximate type of a heavy operation. Used in <code>TrafficLightRenderer</code> UI as brief description.
   */
  public enum Type {
    Load("heavyProcess.type.load"),
    Index("heavyProcess.type.index"),
    Reset("heavyProcess.type.reset"),
    Refresh("heavyProcess.type.refresh"),
    Process("heavyProcess.type.process");

    private final String bundleKey;
    Type(String bundleKey) {
      this.bundleKey = bundleKey;
    }

    @Override
    public String toString() {
      return UtilBundle.message(bundleKey);
    }
  }

  @NotNull
  public AccessToken processStarted(@NotNull String operationName) {
    return processStarted(operationName, Type.Process);
  }

  public AccessToken processStarted(@NotNull String operationName, @NotNull Type type) {
    myHeavyProcesses.put(operationName, type);
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

  @Nullable
  public String getRunningOperationName() {
    Map.Entry<String, Type> runningOperation = getRunningOperation();
    return runningOperation != null ? runningOperation.getKey() : null;
  }

  @Nullable
  public Map.Entry<String, Type> getRunningOperation() {
    if (myHeavyProcesses.isEmpty()) {
      return null;
    }
    else {
      Iterator<Map.Entry<String, Type>> iterator = myHeavyProcesses.entrySet().iterator();
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