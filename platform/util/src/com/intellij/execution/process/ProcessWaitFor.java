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
          processQueue();
          TimeoutUtil.sleep(50);
        }
      }
    });
  }

  private static void processQueue() {
    synchronized (ourQueue) {
      for (Iterator<Process> iterator = ourQueue.keySet().iterator(); iterator.hasNext(); ) {
        Process process = iterator.next();
        try {
          int value = process.exitValue();

          Collection<Consumer<Integer>> callbacks = ourQueue.get(process);
          for (Consumer<Integer> callback : callbacks) {
            callback.consume(value);
          }

          iterator.remove();
        }
        catch (IllegalThreadStateException ignore) { }
        catch (RuntimeException e) {
          LOG.debug(e);
        }
      }
    }
  }

  public static void attach(@NotNull Process process, @NotNull Consumer<Integer> callback) {
    synchronized (ourQueue) {
      ourQueue.putValue(process, callback);
    }
  }

  public static void detach(@NotNull Process process) {
    synchronized (ourQueue) {
      ourQueue.remove(process);
    }
  }
}
