// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy.SharedLockLockingStrategy;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.Assume.assumeTrue;

public class InputStreamOverPagedStorageTest extends InputStreamOverPagedStorageTestBase {

  private static final StorageLockContext CONTEXT = new StorageLockContext(true, true, false);

  private PagedFileStorageWithRWLockedPageContent storage;
  private ReentrantReadWriteLock storageLock;

  @Before
  public void setUp() throws Exception {
    assumeTrue("Can't test lock-free storage if LOCK_FREE_PAGE_CACHE_ENABLED=false", PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED);
    storageLock = new ReentrantReadWriteLock();
    storage =  new PagedFileStorageWithRWLockedPageContent(
      temporaryFolder.newFile().toPath(),
      CONTEXT,
      PAGE_SIZE,
      false,
      new SharedLockLockingStrategy(storageLock)
    );
    storageLock.writeLock().lock();
  }

  @After
  public void tearDown() throws Exception {
    if (storage != null) {
      storage.close();
      storageLock.writeLock().unlock();
    }
  }

  

  @Override
  protected @NotNull InputStreamOverPagedStorage streamOverStorage(long position,
                                                                   long limit) {
    return new InputStreamOverPagedStorage(storage, position, limit);
  }

  @Override
  protected byte[] writeRandomBytesToStorage(int bytesCount) throws IOException {
    byte[] bytesToWrite = randomBytes(bytesCount);
    storage.put(0, bytesToWrite, 0, bytesToWrite.length);
    return bytesToWrite;
  }
}