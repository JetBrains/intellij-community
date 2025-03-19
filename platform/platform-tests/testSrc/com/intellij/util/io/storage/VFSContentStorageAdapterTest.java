// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage;

import com.intellij.openapi.vfs.newvfs.persistent.dev.content.ContentHashEnumeratorOverDurableEnumerator;
import com.intellij.openapi.vfs.newvfs.persistent.dev.content.ContentStorageAdapter;
import com.intellij.util.io.PageCacheUtils;
import com.intellij.util.io.storage.lf.RefCountingContentStorageImplLF;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.intellij.util.io.storage.CapacityAllocationPolicy.FIVE_PERCENT_FOR_GROWTH;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class VFSContentStorageAdapterTest extends VFSContentStorageTestBase<ContentStorageAdapter> {

  private ExecutorService storingPool;

  @BeforeAll
  static void checkLockFreeEnabled() {
    assumeTrue(PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED,
               "ContentHashEnumeratorOverDurableEnumerator expects lock-free FilePageCache to be enabled");
  }

  @Override
  protected @NotNull ContentStorageAdapter openStorage(@NotNull Path storagePath) throws IOException {
    storingPool = Executors.newSingleThreadExecutor(r -> new Thread(r, "ContentWriting Pool"));
    var contentStorage = new RefCountingContentStorageImplLF(storagePath, FIVE_PERCENT_FOR_GROWTH, storingPool, true);
    return new ContentStorageAdapter(
      contentStorage,
      () -> ContentHashEnumeratorOverDurableEnumerator.open(storagePath.resolveSibling(storagePath.getFileName() + ".contentHashes"))
    );
  }

  @Override
  @AfterEach
  void tearDown() throws IOException {
    if (storingPool != null) {
      storingPool.shutdown();
    }
  }
}
