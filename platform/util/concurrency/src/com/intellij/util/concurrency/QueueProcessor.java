// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.CeProcessCanceledException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;

/**
 * <p>QueueProcessor processes elements which are being added to a queue via {@link #add(Object)} and {@link #addFirst(Object)} methods.</p>
 * <p>Elements are processed one by one in a special single thread (AWT or pooled thread).
 * The thread is occupied only when the queue is not empty.
 * Once all items are processed, the thread is released.
 * In case of pooled thread, once new items are added, a new thread is requested for processing.
 * The processor itself is passed in the constructor and is called from that thread.
 * By default processing starts when the first element is added to the queue, though there is an 'autostart' option which holds
 * the processor until {@link #start()} is called.</p>
 * This class is thread-safe.
 * @param <T> type of queue elements.
 *
 * @see com.intellij.util.ui.update.MergingUpdateQueue
 */
public final class QueueProcessor<T> {
  private static final Logger LOG = Logger.getInstance(QueueProcessor.class);

  public enum ThreadToUse {
    AWT,
    POOLED
  }

  private final BiConsumer<? super T, ? super Runnable> myProcessor;
  private final Deque<Pair<T, ModalityState>> myQueue = new ArrayDeque<>();

  private boolean isProcessing;
  private boolean myStarted;

  private final ThreadToUse myThreadToUse;
  private final Condition<?> myDeathCondition;

  /**
   * Constructs a QueueProcessor, which will autostart as soon as the first element is added to it.
   */
  public QueueProcessor(@NotNull Consumer<? super T> processor) {
    this(processor, Conditions.alwaysFalse());
  }

  /**
   * Constructs a QueueProcessor, which will autostart as soon as the first element is added to it.
   */
  public QueueProcessor(@NotNull Consumer<? super T> processor, @NotNull Condition<?> deathCondition) {
    this(processor, deathCondition, true);
  }

  public QueueProcessor(@NotNull java.util.function.Consumer<? super T> processor, @NotNull Condition<?> deathCondition, boolean autostart) {
    this(wrappingProcessor(processor), autostart, ThreadToUse.POOLED, deathCondition);
  }

  public static @NotNull QueueProcessor<Runnable> createRunnableQueueProcessor() {
    return new QueueProcessor<>(new RunnableConsumer(), Conditions.alwaysFalse(), true);
  }

  public static @NotNull QueueProcessor<Runnable> createRunnableQueueProcessor(@NotNull ThreadToUse threadToUse) {
    return new QueueProcessor<>(wrappingProcessor(new RunnableConsumer()), true, threadToUse, Conditions.alwaysFalse());
  }

  private static @NotNull <T> BiConsumer<T, Runnable> wrappingProcessor(@NotNull java.util.function.Consumer<? super T> processor) {
    return (item, continuation) -> {
      try {
        runSafely(() -> processor.accept(item));
      }
      finally {
        continuation.run();
      }
    };
  }

  /**
   * Constructs a QueueProcessor with the given processor and autostart setting.
   * By default, QueueProcessor starts processing when it receives the first element.
   * Pass {@code false} to alternate its behavior.
   *
   * @param processor processor of queue elements.
   * @param autostart if {@code true} (which is by default), the queue will be processed immediately when it receives the first element.
   *                  If {@code false}, then it will wait for the {@link #start()} command.
   *                  After QueueProcessor has started once, autostart setting doesn't matter anymore: all other elements will be processed immediately.
   */

  public QueueProcessor(@NotNull BiConsumer<? super T, ? super Runnable> processor,
                        boolean autostart,
                        @NotNull ThreadToUse threadToUse,
                        @NotNull Condition<?> deathCondition) {
    myProcessor = processor;
    myStarted = autostart;
    myThreadToUse = threadToUse;
    myDeathCondition = deathCondition;
  }

  /**
   * Starts queue processing if it hasn't started yet.
   * Effective only if the QueueProcessor was created with no-autostart option: otherwise processing will start as soon as the first element
   * is added to the queue.
   * If there are several elements in the queue, processing starts from the first one.
   */
  public void start() {
    synchronized (myQueue) {
      if (myStarted) return;
      myStarted = true;
      if (!myQueue.isEmpty()) {
        startProcessing();
      }
    }
  }

  private void finishProcessing(boolean continueProcessing) {
    synchronized (myQueue) {
      isProcessing = false;
      if (myQueue.isEmpty()) {
        myQueue.notifyAll();
      }
      else if (continueProcessing){
        startProcessing();
      }
    }
  }

  public void add(@NotNull T t, ModalityState state) {
    doAdd(t, state, false);
  }

  public void add(@NotNull T element) {
    doAdd(element, null,false);
  }

  public void addFirst(@NotNull T element) {
    doAdd(element, null,true);
  }

  private void doAdd(@NotNull T element, @Nullable ModalityState state, boolean atHead) {
    synchronized (myQueue) {
      Pair<T, ModalityState> pair = Pair.create(element, state);
      if (atHead) {
        myQueue.addFirst(pair);
      }
      else {
        myQueue.add(pair);
      }
      startProcessing();
    }
  }

  public void clear() {
    synchronized (myQueue) {
      myQueue.clear();
    }
  }

  public void waitFor() {
    assertCorrectThread();
    synchronized (myQueue) {
      while (isProcessing) {
        try {
          myQueue.wait();
        }
        catch (InterruptedException e) {
          //ok
        }
      }
    }
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public boolean waitFor(long timeoutMS) {
    assertCorrectThread();
    synchronized (myQueue) {
      long start = System.currentTimeMillis();

      while (isProcessing) {
        long rest = timeoutMS - (System.currentTimeMillis() - start);

        if (rest <= 0) return !isProcessing;

        try {
          myQueue.wait(rest);
        }
        catch (InterruptedException e) {
          //ok
        }
      }

      return true;
    }
  }

  private void assertCorrectThread() {
    if (myThreadToUse == ThreadToUse.AWT) {
      ApplicationManager.getApplication().assertIsNonDispatchThread();
    }
  }

  private void startProcessing() {
    LOG.assertTrue(Thread.holdsLock(myQueue));

    if (isProcessing || !myStarted) {
      return;
    }
    isProcessing = true;
    Pair<T, ModalityState> pair = myQueue.removeFirst();
    T item = pair.getFirst();
    Runnable runnable = () -> {
      if (myDeathCondition.value(null)) {
        finishProcessing(false);
        return;
      }
      runSafely(() -> myProcessor.accept(item, (Runnable)() -> finishProcessing(true)));
    };
    Application application = ApplicationManager.getApplication();
    switch (myThreadToUse) {
      case AWT -> {
        ModalityState state = pair.getSecond();
        if (state == null) {
          application.invokeLater(runnable);
        }
        else {
          application.invokeLater(runnable, state);
        }
      }
      case POOLED -> {
        if (application == null) {
          SwingUtilities.invokeLater(runnable);
        }
        else {
          AppJavaExecutorUtil.executeOnPooledIoThread(runnable);
        }
      }
    }
  }

  public static void runSafely(@NotNull Runnable run) {
    try {
      run.run();
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (CancellationException e) {
      throw new CeProcessCanceledException(e);
    }
    catch (Throwable e) {
      Application application = ApplicationManager.getApplication();
      if (application != null && application.isUnitTestMode()) {
        throw e;
      }
      try {
        LOG.error(e);
      }
      catch (Throwable e2) {
        //noinspection CallToPrintStackTrace
        e2.printStackTrace();
      }
    }
  }

  public boolean isEmpty() {
    synchronized (myQueue) {
      return myQueue.isEmpty() && !isProcessing;
    }
  }

  /**
   * Removes several last tasks in the queue, leaving only {@code remaining} amount of them, counted from the head of the queue.
   */
  public void dismissLastTasks(int remaining) {
    synchronized (myQueue) {
      while (myQueue.size() > remaining) {
        myQueue.pollLast();
      }
    }
  }

  public boolean hasPendingItemsToProcess() {
    synchronized (myQueue) {
      return !myQueue.isEmpty();
    }
  }

  private static final class RunnableConsumer implements java.util.function.Consumer<Runnable> {
    @Override
    public void accept(Runnable runnable) {
      runnable.run();
    }
  }
}
