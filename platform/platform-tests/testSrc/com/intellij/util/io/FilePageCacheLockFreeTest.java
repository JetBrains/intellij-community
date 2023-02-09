// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.*;

/**
 *
 */
public class FilePageCacheLockFreeTest {

  private static final long CACHE_CAPACITY_BYTES = 1 << 25;//32M
  private static final int PAGE_SIZE = 1024;

  @Rule
  public final TemporaryFolder tmpDirectory = new TemporaryFolder();

  @Test
  public void housekeeperThreadNotStartUntilFirstStorageRegistered() throws Exception {
    final FilePageCacheLockFree fpCache = new FilePageCacheLockFree(CACHE_CAPACITY_BYTES);
    try (fpCache) {
      Thread.sleep(1000L);
    }
    assertEquals(
      "Housekeeper thread shouldn't start until first storage registered",
      0,
      fpCache.getStatistics().housekeeperTurnDone()
    );
  }

  @Test
  public void housekeeperThreadStartsOnFirstStorageRegistered() throws Exception {
    final File file = tmpDirectory.newFile();

    final FilePageCacheLockFree fpCache = new FilePageCacheLockFree(CACHE_CAPACITY_BYTES);
    try (fpCache) {
      final StorageLockContext storageContext = new StorageLockContext(fpCache, true, true, true);
      try (final PagedFileStorageLockFree storage = new PagedFileStorageLockFree(file.toPath(), storageContext, PAGE_SIZE, true)) {
        Thread.sleep(1000L);
      }
    }
    assertTrue(
      "Housekeeper thread shouldn't start until first storage registered",
      fpCache.getStatistics().housekeeperTurnDone() > 0
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
    final File file = tmpDirectory.newFile();

    final FilePageCacheLockFree fpCache = new FilePageCacheLockFree(CACHE_CAPACITY_BYTES);
    fpCache.close();

    final StorageLockContext storageContext = new StorageLockContext(fpCache, true, true, true);
    assertThrows(
      "Open storage with closed FilePageCache is prohibited",
      IllegalStateException.class,
      () -> {
        //noinspection EmptyTryBlock
        try (PagedFileStorageLockFree storage = new PagedFileStorageLockFree(file.toPath(), storageContext, PAGE_SIZE, true)) {
        }
      });
  }
}