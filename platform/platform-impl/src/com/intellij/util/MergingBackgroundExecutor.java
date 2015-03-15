package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes tasks on pooled threads. At any point, at most {@code maxThreads} threads will be active processing tasks.
 * If additional tasks are submitted when all threads are active, they will wait in the queue until a thread is available.
 *
 * Difference to {@link java.util.concurrent.Executors#newFixedThreadPool(int)} is that this utility class
 * allows to reuse shared thread pool and thus getting rid of extra thread creation and thread pool management.
 *
 * @param <T> the type of elements
 */
public class MergingBackgroundExecutor<T> {

  private static final Logger LOG = Logger.getInstance(MergingBackgroundExecutor.class);

  private final int myMaxThreads;
  private final Consumer<T> myConsumer;
  private final BlockingQueue<T> myQueue = new LinkedBlockingDeque<T>();
  private final AtomicInteger myRunningThreads = new AtomicInteger(0);

  public MergingBackgroundExecutor(int maxThreads, @NotNull Consumer<T> consumer) {
    myMaxThreads = maxThreads;
    myConsumer = consumer;
  }

  protected void executeOnPooledThread(@NotNull Runnable runnable) {
    ApplicationManager.getApplication().executeOnPooledThread(runnable);
  }

  public void queue(@NotNull T t) {
    if (!myQueue.offer(t)) {
      LOG.error("Unable to enqueue an element, queue size: " + myQueue.size());
      return;
    }
    if (incrementIfSmaller(myRunningThreads, myMaxThreads)) {
      executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          do {
            try {
              processQueue();
            }
            finally {
              myRunningThreads.decrementAndGet();
            }
            // Defense from unlucky timing:
            // An element could be enqueued between "processQueue()" and "myRunningThreads.decrementAndGet()".
            // As a result, "executeOnPooledThread(Runnable)" won't be called.
            // In this case the queue processing should be started over.
          }
          while (!myQueue.isEmpty() && incrementIfSmaller(myRunningThreads, myMaxThreads));
        }
      });
    }
  }

  private static boolean incrementIfSmaller(@NotNull AtomicInteger i, int max) {
    int value;
    do {
      value = i.get();
      if (value >= max) {
        return false;
      }
    }
    while (!i.compareAndSet(value, value + 1));
    return true;
  }

  private void processQueue() {
    T t;
    while ((t = myQueue.poll()) != null) {
      myConsumer.consume(t);
    }
  }

  @NotNull
  public static MergingBackgroundExecutor<Runnable> newRunnableExecutor(int maxThreads) {
    return new MergingBackgroundExecutor<Runnable>(maxThreads, new Consumer<Runnable>() {
      @Override
      public void consume(Runnable runnable) {
        runnable.run();
      }
    });
  }
}
