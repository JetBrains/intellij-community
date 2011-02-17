/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Allows to process elements in the EDT.
 * Processes elements in batches, no longer than 200ms per batch, and reschedules processing later for longer batches.
 * Usage: {@link TransferToEDTQueue#offer(Object)} } : schedules element for processing in EDT (via invokeLater)
 */
public class TransferToEDTQueue<T> {
  private final Processor<T> myProcessor;
  private volatile boolean stopped;
  private final Condition<?> myShutUpCondition;

  private final Queue<T> myQueue = new ConcurrentLinkedQueue<T>();
  private final AtomicBoolean invokeLaterScheduled = new AtomicBoolean();
  private static final long MAX_UNIT_OF_WORK_THRESHOLD_MS = 200; // no more than 200 ms delay
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
      int processed = 0;
      while (true) {
        T thing = myQueue.poll();
        if (thing == null) break;
        if (!myProcessor.process(thing)) {
          myQueue.clear();
          return;
        }
        processed++;
        long finish = System.currentTimeMillis();
        if (finish - start > MAX_UNIT_OF_WORK_THRESHOLD_MS) break;
      }
      if (!myQueue.isEmpty()) {
        scheduleUpdate();
      }
    }
  };

  public TransferToEDTQueue(@NotNull Processor<T> processorInEDT, @NotNull Condition<?> shutUpCondition) {
    myProcessor = processorInEDT;
    myShutUpCondition = shutUpCondition;
  }

  public void offer(@NotNull T thing) {
    myQueue.offer(thing);
    scheduleUpdate();
  }

  private void scheduleUpdate() {
    if (invokeLaterScheduled.compareAndSet(false, true)) {
      schedule(myUpdateRunnable);
    }
  }

  protected void schedule(Runnable updateRunnable) {
    SwingUtilities.invokeLater(updateRunnable);
  }

  public void stop() {
    stopped = true;
    myQueue.clear();
  }
}
