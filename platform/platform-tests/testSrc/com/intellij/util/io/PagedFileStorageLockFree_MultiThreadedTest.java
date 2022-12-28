// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.pagecache.Page;
import org.junit.Test;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
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
    //assumeTrue(
    //  "LockFree FilePageCache must be enabled: see PageCacheUtils.LOCK_FREE_VFS_ENABLED",
    //  PageCacheUtils.LOCK_FREE_VFS_ENABLED
    //);
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