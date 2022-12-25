// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.FilePageCacheLockFree.Page;
import com.intellij.util.io.FilePageCacheLockFree.PageToStorageHandle;
import com.intellij.util.io.pagecache.PageStorageHandleHelper;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.Assert.*;

/**
 *
 */
public class PageTest {

  /** All available CPUs -- except 1 which runs 'main' anyway */
  public static final int THREADS = Runtime.getRuntime().availableProcessors() - 1;

  @Test
  public void dirtyRegionUpdatesDoesNotMissUpdatesUnderConcurrentExecution() throws Exception {
    final int blockSize = 32;
    final int blocksPerThread = 8;
    final int threadsCount = 2;

    final int pageSize = blockSize * threadsCount * blocksPerThread;
    final byte[] backingStorage = new byte[pageSize];

    final byte[] block = generateRandomByteArray(blockSize);

    final PageToStorageHandle handle = new PageStorageHandleHelper() {
      @Override
      public void flushBytes(final @NotNull ByteBuffer dataToFlush,
                             final long offsetInFile) {
        dataToFlush.get(backingStorage,
                        (int)offsetInFile, dataToFlush.remaining());
      }
    };

    final Page page = new Page(0, pageSize, handle);
    page.prepareForUse(_page -> ByteBuffer.allocate(_page.pageSize()));
    page.tryAcquireForUse(this);

    final Collection<Callable<Void>> tasks = IntStream.range(0, threadsCount)
      .mapToObj(threadNo -> (Callable<Void>)() -> {
                  //each thread writes blocksPerThread blocks, but starting with own offset, so
                  // blocks of different threads not overlap
                  for (int blockNo = 0; blockNo < blocksPerThread; blockNo++) {
                    final int offset = ((blockNo * threadsCount) + threadNo) * blockSize;
                    page.write(offset, blockSize,
                               pageBuffer -> pageBuffer.put(block));
                  }
                  return null;
                }
      ).toList();


    final ExecutorService pool = Executors.newFixedThreadPool(threadsCount);
    try {
      for (int turn = 0; turn < 100_000; turn++) {
        final List<Future<Void>> futures = ContainerUtil.map(tasks, pool::submit);
        do {
          page.flush();
        }
        while (!futures.stream().allMatch(Future::isDone));

        page.flush();

        //assert backingStorage is filled with repeating 'block' bytes:
        for (int blockNo = 0; blockNo < blocksPerThread * threadsCount; blockNo++) {
          final int startOffset = blockNo * blockSize;
          final byte[] _block = Arrays.copyOfRange(backingStorage, startOffset, startOffset + blockSize);
          if (!Arrays.equals(block, _block)) {
            fail(
              "turn[" + turn + "], block[#" + blockNo + "].range[" + startOffset + ".." + (startOffset + blockSize) + "): \n" +
              "expected: " + IOUtil.toHexString(block) + "\n" +
              " but was: " + IOUtil.toHexString(_block) + "\n"
            );
          }
        }
        //clean backingStorage before next turn:
        Arrays.fill(backingStorage, (byte)0);
      }
    }
    finally {
      pool.shutdown();
      pool.awaitTermination(10, MINUTES);
    }
  }

  @Test
  public void pageAndStorageDirtyStatusesAreConsistent_EvenWithConcurrentFlushes() throws Exception {

    final DirtyStatusCounter dirtinessCounter = new DirtyStatusCounter();
    final Page page = new Page(0, 1024, dirtinessCounter);
    page.prepareForUse(_page -> ByteBuffer.allocate(_page.pageSize()));
    page.tryAcquireForUse(this);

    final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      final Callable<Void> flushingTask = () -> {
        page.flush();

        assertEquals(
          "Dirty pages must be 0 since the only page is just successfully .flush()-ed",
          0,
          dirtinessCounter.dirtyPagesCount()
        );

        return null;
      };
      final List<Callable<Void>> flushingTasks = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        flushingTasks.add(flushingTask);
      }

      for (int turn = 0; turn < 100_000; turn++) {
        page.putLong(0, Long.MAX_VALUE);    //make the page dirty

        final List<Future<Void>> futures = pool.invokeAll(flushingTasks);

        assertEquals("Dirty pages must be 0 since the only page is just successfully .flush()-ed",
                     0,
                     dirtinessCounter.dirtyPagesCount()
        );

        for (final Future<Void> future : futures) {
          try {
            future.get();
          }
          catch (ExecutionException e) {
            throw new AssertionError("Turn[#" + turn + "] ", e.getCause());
          }
        }
      }
    }
    finally {
      pool.shutdown();
      assertTrue(
        "Pool must terminate in 10 min",
        pool.awaitTermination(10, MINUTES)
      );
    }
  }


  //FIXME RC: test is flaky due to racy .flush() which competes with eager flushes in housekeeper thread
  //FIXME RC: now another reason for this test to fail is BusyWaitingBarrier -- it seems flawed itself
  //          (see next test)
  @Test
  @Ignore("See the comments above")
  public void pageAndStorageDirtyStatusesAreConsistent_EvenWithConcurrentFlushes_WithBusyWaitBarrier() throws Exception {
    final DirtyStatusCounter dirtinessCounter = new DirtyStatusCounter();
    final Page page = new Page(0, 1024, dirtinessCounter);
    page.prepareForUse(_page -> ByteBuffer.allocate(_page.pageSize()));
    page.tryAcquireForUse(this);

    final Thread[] threads = new Thread[THREADS];

    try (BusyWaitingBarrier barrier = new BusyWaitingBarrier(THREADS)) {
      final AtomicReference<AssertionError> error = new AtomicReference<>(null);

      for (int i = 0; i < threads.length; i++) {
        threads[i] = new Thread(() -> {
          try {
            while (!Thread.currentThread().isInterrupted()) {
              barrier.waitOnBarrier();

              try {
                page.flush();
              }
              catch (IOException e) {
                //not possible since we don't do any IO really
                e.printStackTrace();
              }

              final int dirtyPages = dirtinessCounter.dirtyPagesCount();
              if (dirtyPages != 0) {
                error.set(
                  new AssertionError("Dirty pages(=" + dirtyPages + ") must be 0 since the only page is just successfully .flush()-ed")
                );
              }
            }
            System.out.println("Interrupted -> exit");
          }
          catch (InterruptedException e) {
            System.out.println("Interrupted -> exit");
          }
        }, "thread-" + i);

        threads[i].start();
      }

      barrier.waitForAllThreadsToCome();
      for (int i = 0; i < 100_000; i++) {
        page.putLong(0, Long.MAX_VALUE);    //make the page dirty

        barrier.openBarrierAndWaitForAllThreadsToPass();

        barrier.waitForAllThreadsToCome();               //wait for all threads to pass .flush()
        assertEquals("Dirty pages must be 0 since the only page is just successfully .flush()-ed",
                     0,
                     dirtinessCounter.dirtyPagesCount()
        );

        final AssertionError errorFromThread = error.get();
        if (errorFromThread != null) {
          //None of the threads should see dirtyPagesCount>0 after its own flush() completed
          throw new AssertionError("Turn: " + i, errorFromThread);
        }
      }
    }

    for (Thread thread : threads) {
      thread.join();
    }
  }

  //FIXME RC: it looks like a bug in jvm(!), but I want to check it against other JVM/OS versions
  @Test
  @Ignore("See the comments above")
  public void busyWaitingBarrierWorkConsistently() throws Exception {
    final Thread[] threads = new Thread[THREADS];

    try (BusyWaitingBarrier barrier = new BusyWaitingBarrier(THREADS)) {
      for (int i = 0; i < threads.length; i++) {
        threads[i] = new Thread(() -> {
          try {
            final Thread currentThread = Thread.currentThread();
            final long threadId = currentThread.getId();
            while (!currentThread.isInterrupted()) {
              final int orderInQueue = barrier.waitOnBarrier();
              if (orderInQueue >= 0) {
                System.out.println("Thread[#" + threadId + "]: " + orderInQueue + "-th in queue");
              }
            }
            System.out.println("Interrupted -> exit");
          }
          catch (InterruptedException e) {
            System.out.println("Interrupted -> exit");
          }
        }, "thread-" + i);

        threads[i].start();
      }

      for (int i = 0; i < 100_000; i++) {
        final int threadsWaiting = barrier.waitForAllThreadsToCome();

        //FIXME RC: number of waiting threads inside openBarrierAndWaitForAllThreadsToPass()
        // is sometimes (THREADS-1), even though previous call must ensure ALL threads come
        // to the barrier and wait, and on the method exit it is explicitly checked
        // waitingThreads==THREADS, and returned value threadsWaiting == THREADS
        barrier.openBarrierAndWaitForAllThreadsToPass();
      }
    }

    for (Thread thread : threads) {
      thread.join();
    }
  }

  private static byte[] generateRandomByteArray(final int length) {
    final byte[] array = new byte[length];
    for (int i = 0; i < array.length; i++) {
      array[i] = (byte)ThreadLocalRandom.current().nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE);
    }
    return array;
  }

  private static class BusyWaitingBarrier implements AutoCloseable {
    private final int threadsCount;

    private final AtomicInteger threadsWaitingOnBarrier = new AtomicInteger(0);
    private final AtomicInteger threadsAllowedToPassBarrier = new AtomicInteger(0);

    private volatile boolean closed = false;

    private BusyWaitingBarrier(final int count) { threadsCount = count; }

    public int waitOnBarrier() throws InterruptedException {
      final int nth = threadsWaitingOnBarrier.incrementAndGet();
      try {
        while (true) {//spinning
          final int allowedToPass = threadsAllowedToPassBarrier.get();
          if (allowedToPass > 0) {
            if (threadsAllowedToPassBarrier.compareAndSet(allowedToPass, allowedToPass - 1)) {
              return nth;
            }
          }
          checkClosed();
        }
      }
      finally {
        threadsWaitingOnBarrier.decrementAndGet();
      }
    }

    public BusyWaitingBarrier openBarrierAndWaitForAllThreadsToPass() throws InterruptedException {
      //waitForAllThreadsToCome();
      final int threadsWaiting = threadsWaitingOnBarrier.get();
      if (threadsWaiting != threadsCount) {
        throw new IllegalStateException(threadsWaiting + " waiting, but must be " + threadsCount);
      }
      final int allowedToPass = threadsAllowedToPassBarrier.get();
      if (allowedToPass != 0) {
        throw new IllegalStateException(allowedToPass + " must be 0");
      }

      //open the barrier:
      threadsAllowedToPassBarrier.addAndGet(threadsCount);

      waitForAllThreadsToPass();
      return this;
    }

    public int waitForAllThreadsToCome() throws InterruptedException {
      final int allowedToPass = threadsAllowedToPassBarrier.get();
      if (allowedToPass > 0) {
        throw new IllegalStateException("threadsAllowedToPassBarrier(=" + allowedToPass + ") must be 0");
      }
      while (threadsWaitingOnBarrier.get() < threadsCount) {
        //spinning
        checkClosed();
      }

      final int threadsWaiting = threadsWaitingOnBarrier.get();
      if (threadsWaiting != threadsCount) {
        throw new IllegalStateException(threadsWaiting + " waiting, but must be " + threadsCount);
      }
      return threadsWaiting;
    }

    private BusyWaitingBarrier waitForAllThreadsToPass() throws InterruptedException {
      while (threadsAllowedToPassBarrier.get() > 0) {
        //spinning
        checkClosed();
      }
      return this;
    }

    private void checkClosed() throws InterruptedException {
      if (isClosed()) {
        throw new InterruptedException("Barrier was closed");
      }
    }

    @Override
    public void close() throws Exception {
      closed = true;
    }

    public boolean isClosed() {
      return closed;
    }
  }

  private static class DirtyStatusCounter extends PageStorageHandleHelper {
    private final AtomicInteger dirtyPagesCount = new AtomicInteger(0);
    private final AtomicReference<Thread> cleaningThread = new AtomicReference<>();

    @Override
    public void pageBecomeDirty() {
      final int dirtyPages = dirtyPagesCount.incrementAndGet();
      cleaningThread.set(null);
      if (dirtyPages != 1) {
        throw new AssertionError("Should be only one dirty page: " + dirtyPages);
      }
    }

    @Override
    public void pageBecomeClean() {
      final int dirtyPages = dirtyPagesCount.decrementAndGet();
      cleaningThread.set(Thread.currentThread());
      if (dirtyPages != 0) {
        throw new AssertionError("Should be 0 dirty pages: " + dirtyPages);
      }
    }

    public int dirtyPagesCount() {
      return dirtyPagesCount.get();
    }
  }
}
