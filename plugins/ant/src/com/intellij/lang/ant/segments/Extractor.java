// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.segments;

import com.intellij.execution.testframework.Printable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.rt.ant.execution.PacketProcessor;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

public class Extractor implements Disposable {
  private static final int MAX_TASKS_TO_PROCESS_AT_ONCE = 100;

  private DeferredActionsQueue myFulfilledWorkGate = null;
  private final SegmentedInputStream myStream;
  private OutputPacketProcessor myEventsDispatcher;
  private static final Logger LOG = Logger.getInstance(Extractor.class);
  private final ExecutorService myExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Ant Extractor Pool");
  private final BlockingQueue<Runnable> myTaskQueue = new LinkedBlockingQueue<>();

  public Extractor(@NotNull InputStream stream, @NotNull Charset charset) {
    myStream = new SegmentedInputStream(stream, charset);
  }

  public void setDispatchListener(final DispatchListener listener) {
    myFulfilledWorkGate.setDispactchListener(listener);
  }

  @Override
  public void dispose() {
    // wait until all our submitted tasks are executed
    try {
      myExecutor.submit(EmptyRunnable.getInstance()).get();
    }
    catch (Exception ignored) {
    }
  }

  public void setPacketDispatcher(final @NotNull PacketProcessor packetProcessor, final DeferredActionsQueue queue) {
    myFulfilledWorkGate = new DeferredActionsQueue() { //todo make it all later
      @Override
      public void addLast(final Runnable runnable) {
        scheduleTask(queue, runnable);
      }

      @Override
      public void setDispactchListener(final DispatchListener listener) {
        queue.setDispactchListener(listener);
      }
    };
    myEventsDispatcher = new OutputPacketProcessor() {
      @Override
      public void processPacket(final String packet) {
        myFulfilledWorkGate.addLast(() -> packetProcessor.processPacket(packet));
      }

      @Override
      public void processOutput(final Printable printable) {
        LOG.assertTrue(packetProcessor instanceof OutputPacketProcessor);
        myFulfilledWorkGate.addLast(() -> ((OutputPacketProcessor)packetProcessor).processOutput(printable));
      }
    };
    myStream.setEventsDispatcher(myEventsDispatcher);
  }

  private void scheduleTask(final DeferredActionsQueue queue, final Runnable task) {
    myTaskQueue.add(task);
    myExecutor.execute(() -> {
      final List<Runnable> currentTasks = new ArrayList<>(MAX_TASKS_TO_PROCESS_AT_ONCE);
      if (myTaskQueue.drainTo(currentTasks, MAX_TASKS_TO_PROCESS_AT_ONCE) > 0) {
        // there is a requirement that these activities must be run from the swing thread
        // will be blocking one of pooled threads here, which is ok
        try {
          SwingUtilities.invokeAndWait(() -> {
            for (Runnable task1 : currentTasks) {
              try {
                queue.addLast(task1);
              }
              catch (Throwable e) {
                LOG.info(e);
              }
            }
          });
        }
        catch (Throwable e) {
          LOG.info("Task rejected: " + currentTasks, e);
        }
      }
    });
  }

  public OutputPacketProcessor getEventsDispatcher() {
    return myEventsDispatcher;
  }

  public Reader createReader() {
    return new SegmentedInputStreamReader(myStream);
  }

  public void addRequest(final Runnable runnable, final DeferredActionsQueue queue) {
    scheduleTask(queue, runnable);
  }
}
