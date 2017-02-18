/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.concurrency.QueueProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * Queue with a limited number of tasks, and with higher priority for new tasks, than for older ones.
 */
public class SequentialLimitedLifoExecutor<Task> implements Disposable {

  private final int myMaxTasks;
  @NotNull private final ThrowableConsumer<Task, ? extends Throwable> myLoadProcess;
  @NotNull private final QueueProcessor<Task> myLoader;

  public SequentialLimitedLifoExecutor(Disposable parentDisposable, int maxTasks,
                                       @NotNull ThrowableConsumer<Task, ? extends Throwable> loadProcess) {
    myMaxTasks = maxTasks;
    myLoadProcess = loadProcess;
    myLoader = new QueueProcessor<>(new DetailsLoadingTask());
    Disposer.register(parentDisposable, this);
  }

  public void queue(Task task) {
    myLoader.addFirst(task);
  }

  public void clear() {
    myLoader.clear();
  }

  @Override
  public void dispose() {
    clear();
  }

  private class DetailsLoadingTask implements Consumer<Task> {
    @Override
    public void consume(final Task task) {
      try {
        myLoader.dismissLastTasks(myMaxTasks);
        myLoadProcess.consume(task);
      }
      catch (Throwable e) {
        throw new RuntimeException(e); // todo
      }
    }
  }
}
