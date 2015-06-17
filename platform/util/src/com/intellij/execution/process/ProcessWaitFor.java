/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Consumer;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class ProcessWaitFor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.ProcessWaitFor");

  private static final MultiMap<Process, Consumer<Integer>> ourQueue;

  static {
    ourQueue = new MultiMap<Process, Consumer<Integer>>();

    BaseOSProcessHandler.ExecutorServiceHolder.submit(new Runnable() {
      @Override
      public void run() {
        //noinspection InfiniteLoopStatement
        while (true) {
          try {
            processQueue();
            TimeoutUtil.sleep(50);
          }
          catch (ThreadDeath e) {
            throw e;
          }
          catch (Throwable t) {
            LOG.error(t);
          }
        }
      }
    });
  }

  private static void processQueue() {
    synchronized (ourQueue) {
      for (Iterator<Map.Entry<Process, Collection<Consumer<Integer>>>> iterator = ourQueue.entrySet().iterator(); iterator.hasNext(); ) {
        Map.Entry<Process, Collection<Consumer<Integer>>> entry = iterator.next();
        try {
          int value = entry.getKey().exitValue();

          for (Consumer<Integer> callback : entry.getValue()) {
            try {
              callback.consume(value);
            }
            catch (Throwable t) {
              LOG.error(t);
            }
          }

          iterator.remove();
        }
        catch (IllegalThreadStateException ignore) { }
      }
    }
  }

  public static void attach(@NotNull Process process, @NotNull Consumer<Integer> callback) {
    synchronized (ourQueue) {
      ourQueue.putValue(process, callback);
    }
  }


  public static void detach(@NotNull Process process, Consumer<Integer> callback) {
    if (callback != null) {
      synchronized (ourQueue) {
        ourQueue.remove(process, callback);
      }
    }
  }
}
