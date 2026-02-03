// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.concurrency.QueueProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Queue with a limited number of tasks, and with higher priority for new tasks, than for older ones.
 */
@ApiStatus.Internal
public class SequentialLimitedLifoExecutor<Task> implements Disposable {
  private static final Logger LOG = Logger.getInstance(SequentialLimitedLifoExecutor.class);

  private final int myMaxTasks;
  private final @NotNull ThrowableConsumer<? super Task, ? extends Throwable> myLoadProcess;
  private final @NotNull QueueProcessor<Task> myLoader;

  public SequentialLimitedLifoExecutor(Disposable parentDisposable, int maxTasks,
                                       @NotNull ThrowableConsumer<? super Task, ? extends Throwable> loadProcess) {
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
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }
}
