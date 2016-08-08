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

/**
 * @author dyoma
 */
public class Extractor implements Disposable {
  private static final int MAX_TASKS_TO_PROCESS_AT_ONCE = 100;
  
  private DeferredActionsQueue myFulfilledWorkGate = null;
  private final SegmentedInputStream myStream;
  private OutputPacketProcessor myEventsDispatcher;
  private static final Logger LOG = Logger.getInstance("#" + Extractor.class.getName());
  private final ExecutorService myExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor();
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

  public void setPacketDispatcher(@NotNull final PacketProcessor packetProcessor, final DeferredActionsQueue queue) {
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
