// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Allows to process elements in the EDT.
 * Processes elements in batches, no longer than 200ms (or maxUnitOfWorkThresholdMs constructor parameter) per batch,
 * and reschedules processing later for longer batches.
 * Usage: {@link TransferToEDTQueue#offer(Object)} } : schedules element for processing in EDT (via invokeLater)
 * @deprecated use {@link com.intellij.util.concurrency.EdtExecutorService} instead
 */
@Deprecated
public class TransferToEDTQueue<T> {
  /**
   * This is a default threshold used to join units of work.
   * It allows us to generate more than 30 frames per second.
   * It is not recommended to block EDT longer,
   * because people feel that UI is laggy.
   *
   * @see #TransferToEDTQueue(String, Processor, Condition, int)
   * @see #createRunnableMerger(String, int)
   */
  private static final int DEFAULT_THRESHOLD = 30;
  private final String myName;
  private final Processor<? super T> myProcessor;
  private volatile boolean stopped;
  private final Condition<?> myShutUpCondition;
  private final int myMaxUnitOfWorkThresholdMs; //-1 means indefinite

  private final Deque<T> myQueue = new ArrayDeque<>(10); // guarded by myQueue
  private final AtomicBoolean invokeLaterScheduled = new AtomicBoolean();
  private final Runnable myUpdateRunnable = new Runnable() {
    @Override
    public void run() {
      boolean b = invokeLaterScheduled.compareAndSet(true, false);
      assert b;
      if (stopped || myShutUpCondition.value(null)) {
        stop();
        return;
      }
      long start = System.currentTimeMillis();
      while (processNext()) {
        long finish = System.currentTimeMillis();
        if (myMaxUnitOfWorkThresholdMs != -1 && finish - start > myMaxUnitOfWorkThresholdMs) break;
      }
      if (!isEmpty()) {
        scheduleUpdate();
      }
    }

    @Override
    public String toString() {
      return TransferToEDTQueue.this.getClass().getSimpleName() + "[" + myName + "]";
    }
  };

  public TransferToEDTQueue(@NotNull @NonNls String name,
                            @NotNull Processor<? super T> processor,
                            @NotNull Condition<?> shutUpCondition,
                            int maxUnitOfWorkThresholdMs) {
    myName = name;
    myProcessor = processor;
    myShutUpCondition = shutUpCondition;
    myMaxUnitOfWorkThresholdMs = maxUnitOfWorkThresholdMs;
  }

  public static @NotNull TransferToEDTQueue<Runnable> createRunnableMerger(@NotNull @NonNls String name) {
    return createRunnableMerger(name, DEFAULT_THRESHOLD);
  }

  public static @NotNull TransferToEDTQueue<Runnable> createRunnableMerger(@NotNull @NonNls String name, int maxUnitOfWorkThresholdMs) {
    return new TransferToEDTQueue<>(name, runnable -> {
      runnable.run();
      return true;
    }, Conditions.alwaysFalse(), maxUnitOfWorkThresholdMs);
  }

  private boolean isEmpty() {
    synchronized (myQueue) {
      return myQueue.isEmpty();
    }
  }

  // return true if element was pulled from the queue and processed successfully
  private boolean processNext() {
    T thing = pollFirst();
    if (thing == null) return false;
    if (!myProcessor.process(thing)) {
      stop();
      return false;
    }
    return true;
  }

  private T pollFirst() {
    synchronized (myQueue) {
      return myQueue.pollFirst();
    }
  }

  public boolean offer(@NotNull T thing) {
    synchronized (myQueue) {
      myQueue.addLast(thing);
    }
    scheduleUpdate();
    return true;
  }

  public boolean offerIfAbsent(@NotNull T thing) {
    synchronized (myQueue) {
      boolean absent = !myQueue.contains(thing);
      if (absent) {
        myQueue.addLast(thing);
        scheduleUpdate();
      }
      return absent;
    }
  }

  private void scheduleUpdate() {
    if (!stopped && invokeLaterScheduled.compareAndSet(false, true)) {
      schedule(myUpdateRunnable);
    }
  }

  protected void schedule(@NotNull Runnable updateRunnable) {
    SwingUtilities.invokeLater(updateRunnable);
  }

  public void stop() {
    stopped = true;
    synchronized (myQueue) {
      myQueue.clear();
    }
  }

  public int size() {
    synchronized (myQueue) {
      return myQueue.size();
    }
  }

  @TestOnly
  public @NotNull Collection<T> dump() {
    synchronized (myQueue) {
      return new ArrayList<>(myQueue);
    }
  }

  // process all queue in current thread
  public void drain() {
    int processed = 0;
    while (processNext()) {
      processed++;
    }
  }
}
