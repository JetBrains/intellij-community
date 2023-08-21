// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.pagecache.FilePageCacheStatistics;
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class FilePageCacheLockFreeTest {

  private static final long CACHE_CAPACITY_BYTES = 1 << 25;//32M
  private static final int PAGE_SIZE = 1024;

  @Rule
  public final TemporaryFolder tmpDirectory = new TemporaryFolder();

  @Test
  public void housekeeperThreadNotStartUntilFirstStorageRegistered() throws Exception {
    FilePageCacheLockFree fpCache = new FilePageCacheLockFree(CACHE_CAPACITY_BYTES);
    try (fpCache) {
      Thread.sleep(1000L);
    }
    assertEquals(
      "Housekeeper thread shouldn't start until first storage registered",
      0,
      fpCache.getStatistics().housekeeperTurnsDone()
    );
  }

  @Test
  public void housekeeperThreadStartsOnFirstStorageRegistered() throws Exception {
    File file = tmpDirectory.newFile();

    FilePageCacheLockFree fpCache = new FilePageCacheLockFree(CACHE_CAPACITY_BYTES);
    try (fpCache) {
      final StorageLockContext storageContext = new StorageLockContext(fpCache, true, true, true);
      try (PagedFileStorageWithRWLockedPageContent storage = createStorage(file, storageContext)) {
        Thread.sleep(1000L);
      }
    }
    assertTrue(
      "Housekeeper thread shouldn't start until first storage registered",
      fpCache.getStatistics().housekeeperTurnsDone() > 0
    );
  }

  @Test
  public void closeCouldBeSafelyCalledMoreThanOnce() throws Exception {
    final FilePageCacheLockFree fpCache = new FilePageCacheLockFree(CACHE_CAPACITY_BYTES);
    fpCache.close();
    fpCache.close();
  }

  @Test
  public void openTheStorageWithClosedCacheFails() throws Exception {
    File file = tmpDirectory.newFile();

    FilePageCacheLockFree fpCache = new FilePageCacheLockFree(CACHE_CAPACITY_BYTES);
    fpCache.close();

    StorageLockContext storageContext = new StorageLockContext(fpCache, true, true, true);
    assertThrows(
      "Open storage with closed FilePageCache is prohibited",
      IllegalStateException.class,
      () -> {
        //noinspection EmptyTryBlock
        try (PagedFileStorageWithRWLockedPageContent storage = createStorage(file, storageContext)) {
        }
      });
  }

  @Test
  public void cacheSettlesMostlyIdleAfterEachOperationAndDontDoAnyWorkByItself() throws Exception {
    File file = tmpDirectory.newFile();

    try (FilePageCacheLockFree fpCache = new FilePageCacheLockFree(CACHE_CAPACITY_BYTES)) {
      checkCacheIsVeryQuiet(fpCache);

      StorageLockContext storageContext = new StorageLockContext(fpCache, true, true, true);

      //do SOMETHING
      try (PagedFileStorageWithRWLockedPageContent storage = createStorage(file, storageContext)) {
        try (var page = storage.pageByIndex(0, true)) {
          page.write(0, 1, buffer -> buffer.put(0, (byte)1));
        }
      }

      checkCacheIsVeryQuiet(fpCache);
    }
  }

  private static void checkCacheIsVeryQuiet(@NotNull FilePageCacheLockFree fpCache) throws Exception {
    Thread.sleep(1000);

    FilePageCacheStatistics statistics = fpCache.getStatistics();
    long housekeeperTurnsBefore = statistics.housekeeperTurnsDone();

    Thread.sleep(1000);

    long housekeeperTurnsAfter = statistics.housekeeperTurnsDone();
    assertTrue("Housekeeper thread should do only minor work since there is no external load",
                 housekeeperTurnsAfter <= housekeeperTurnsBefore + 5);
  }

  @NotNull
  private static PagedFileStorageWithRWLockedPageContent createStorage(File file, StorageLockContext storageContext) throws IOException {
    return new PagedFileStorageWithRWLockedPageContent(file.toPath(),
                                                       storageContext,
                                                       PAGE_SIZE,
                                                       PageContentLockingStrategy.LOCK_PER_PAGE);
  }
}