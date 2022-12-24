// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.FilePageCacheLockFree.Page;
import com.intellij.util.io.FilePageCacheLockFree.PageToStorageHandle;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

/**
 *
 */
public class PagedFileStorageLockFree_MultiThreadedTest {

  private static final int DEFAULT_PAGES_COUNT = 64;
  private static final int PAGE_SIZE = 1024;

  @Rule
  public final TemporaryFolder tmpDirectory = new TemporaryFolder();

  @BeforeClass
  public static void beforeClass() throws Exception {
    assumeTrue(
      "LockFree FilePageCache must be enabled: see PageCacheUtils.LOCK_FREE_VFS_ENABLED",
      PageCacheUtils.LOCK_FREE_VFS_ENABLED
    );
  }

  @Test
  public void closedStorageCouldBeReopenedAgainImmediately() throws Exception {
    final int cacheCapacityBytes = PAGE_SIZE * DEFAULT_PAGES_COUNT;
    final File file = tmpDirectory.newFile();
    try (FilePageCacheLockFree filePageCache = new FilePageCacheLockFree(cacheCapacityBytes)) {
      final StorageLockContext storageContext = new StorageLockContext(filePageCache, true, true, true);
      for (int tryNo = 0; tryNo < 1000; tryNo++) {
        try (PagedFileStorageLockFree storage = new PagedFileStorageLockFree(file.toPath(), storageContext, PAGE_SIZE, true)) {
        }
      }
    }
  }

  @Test
  public void storageClosingSuccessfullyClosesPagesInTheMiddleOfInitialization() throws Exception {
    final int tryes = 10_000;
    final int threads = Runtime.getRuntime().availableProcessors();
    final ExecutorService threadPool = Executors.newFixedThreadPool(threads);
    final int cacheCapacityBytes = PAGE_SIZE * DEFAULT_PAGES_COUNT;
    final File file = tmpDirectory.newFile();

    try (FilePageCacheLockFree filePageCache = new FilePageCacheLockFree(cacheCapacityBytes)) {
      final StorageLockContext storageContext = new StorageLockContext(filePageCache, true, true, true);
      for (int tryNo = 0; tryNo < tryes; tryNo++) {
        final List<Future<Void>> futures;
        try (PagedFileStorageLockFree storage = new PagedFileStorageLockFree(file.toPath(), storageContext, PAGE_SIZE, true)) {
          final CountDownLatch latch = new CountDownLatch(1);
          futures = IntStream.range(0, threads)
            .<Callable<Void>>mapToObj(pageNo -> () -> {
              latch.await();
              try (Page page = storage.pageByIndex(pageNo, true)) {
                //do nothing, just get the page from storage
                page.isUsable();
              }
              return null;
            })
            .map(threadPool::submit)
            .toList();

          latch.countDown();
        }
        // -> Now storage is closed, and we should get no AssertionErrors or IllegalStateExceptions
        //    from futures. But we could get ~ IOException("...already closed")
        for (Future<Void> future : futures) {
          try {
            future.get();
          }
          catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof IOException) {
              IOException exception = (IOException)cause;
              if (exception.getMessage().contains("already closed")) {
                //ok, executable
                continue;
              }
            }
            throw e;
          }
        }
      }
    }
  }


  //FIXME RC: test is flaky due to racy .flush() which competes with eager flushes in housekeeper thread
  @Test
  //@Ignore
  public void concurrentFlushesNotificationsIsSerializable() throws Exception {
    final int threadsCount = Runtime.getRuntime().availableProcessors() - 1;

    final AtomicInteger dirtyPagesCount = new AtomicInteger(0);
    final AtomicReference<Thread> cleaningThread = new AtomicReference<>();
    final PageToStorageHandle handle = new PageToStorageHandle() {
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
        Thread.yield();
        final int dirtyPages = dirtyPagesCount.decrementAndGet();
        cleaningThread.set(Thread.currentThread());
        if (dirtyPages != 0) {
          throw new AssertionError("Should be 0 dirty pages: " + dirtyPages);
        }
      }

      @Override
      public void modifiedRegionUpdated(final long startOffsetInFile, final int length) { /*nothing*/ }

      @Override
      public void flushBytes(final @NotNull ByteBuffer dataToFlush, final long offsetInFile) { /*nothing*/ }
    };
    final Page page = new Page(0, 0, 1024, null, handle);
    page.prepareForUse(_page -> ByteBuffer.allocate(_page.pageSize()));
    page.tryAcquireForUse(this);

    final Thread[] threads = new Thread[threadsCount];

    try (BusyWaitingBarrier barrier = new BusyWaitingBarrier(threadsCount)) {
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

              final int dirtyPages = dirtyPagesCount.get();
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
                     dirtyPagesCount.get()
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
  //@Ignore("")
  public void busyWaitingBarrierWorkConsistently() throws Exception {
    final int threadsCount = Runtime.getRuntime().availableProcessors() - 1;

    final Thread[] threads = new Thread[threadsCount];
    try (BusyWaitingBarrier barrier = new BusyWaitingBarrier(threadsCount)) {
      for (int i = 0; i < threads.length; i++) {
        threads[i] = new Thread(() -> {
          try {
            while (!Thread.currentThread().isInterrupted()) {
              barrier.waitOnBarrier();
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

        barrier.openBarrierAndWaitForAllThreadsToPass();
      }
    }

    for (Thread thread : threads) {
      thread.join();
    }
  }


  private static class BusyWaitingBarrier implements AutoCloseable {
    private final int threadsCount;

    private final AtomicInteger threadsWaitingOnBarrier = new AtomicInteger(0);
    private final AtomicInteger threadsAllowedToPassBarrier = new AtomicInteger(0);

    private volatile boolean closed = false;

    private BusyWaitingBarrier(final int count) { threadsCount = count; }

    public BusyWaitingBarrier waitOnBarrier() throws InterruptedException {
      threadsWaitingOnBarrier.incrementAndGet();
      try {
        while (true) {//spinning
          final int allowedToPass = threadsAllowedToPassBarrier.get();
          if (allowedToPass > 0) {
            if (threadsAllowedToPassBarrier.compareAndSet(allowedToPass, allowedToPass - 1)) {
              return this;
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
}