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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

/**
 *
 */
public class PagedFileStorageLockFree_MultiThreadedTest {

  private static final int DEFAULT_PAGES_COUNT = 64;
  private static final int PAGE_SIZE = 1024;

  @Rule
  public final TemporaryFolder tmpDirectory = new TemporaryFolder();


  //FIXME RC: test is flaky due to racy .flush() which competes with eager flushes in housekeeper thread
  @Test
  public void concurrentFlushesNotificationsIsSerializable() throws Exception {
    final AtomicInteger dirtyPagesCount = new AtomicInteger(0);
    final PageToStorageHandle handle = new PageToStorageHandle() {
      @Override
      public void pageBecomeDirty() {
        dirtyPagesCount.incrementAndGet();
      }

      @Override
      public void pageBecomeClean() {
        dirtyPagesCount.decrementAndGet();
      }

      @Override
      public void modifiedRegionUpdated(final long startOffsetInFile, final int length) {
      }

      @Override
      public void flushBytes(final @NotNull ByteBuffer dataToFlush, final long offsetInFile) throws IOException {
      }
    };
    final Page page = new Page(0, 0, 1024, null, handle);
    page.prepareForUse(_page -> ByteBuffer.allocate(_page.pageSize()));
    page.tryAcquireForUse(this);

    final AtomicBoolean running = new AtomicBoolean(true);
    final AtomicInteger goLatch = new AtomicInteger(0);
    final AtomicInteger resetLatch = new AtomicInteger(16);
    final AtomicReference<AssertionError> error = new AtomicReference<>(null);
    final Thread[] threads = new Thread[16];
    for (int i = 0; i < threads.length; i++) {
      threads[i] = new Thread(() -> {
        while (running.get()) {
          while (goLatch.get() == 0) {
          }
          try {
            page.flush();
          }
          catch (IOException e) {
            e.printStackTrace();
          }
          final int dirtyPages = dirtyPagesCount.get();
          if (dirtyPages != 0) {
            error.set(new AssertionError("Dirty pages must be 0 since the only page is just successfully .flush()-ed: " + dirtyPages));
          }
          goLatch.decrementAndGet();
          while (resetLatch.get() == 0) {
          }
          resetLatch.decrementAndGet();
        }
      }, "thread-" + i);

      threads[i].start();
    }
    try {
      for (int i = 0; i < 100_000; i++) {
        page.putLong(0, Long.MAX_VALUE);//make dirty:

        resetLatch.set(0);                   //close reset-latch
        goLatch.set(threads.length);         //open go-latch

        while (goLatch.get() > 0) ;           //wait for all threads to pass .flush()

        assertThat(dirtyPagesCount.get())
          .describedAs("Dirty pages must be 0 since the only page is just successfully .flush()-ed")
          .isEqualTo(0);
        final AssertionError errorFromThread = error.get();
        if (errorFromThread != null) {
          //None of the threads should see dirtyPagesCount>0 after its own flush() completed
          throw errorFromThread;
        }

        resetLatch.set(threads.length);      //open reset-latch
        while (resetLatch.get() > 0) ;        //wait for all threads to return to go-latch
      }
    }
    finally {
      running.set(false);
      resetLatch.set(threads.length);      //open reset-latch
    }

    for (Thread thread : threads) {
      thread.join();
    }
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
}