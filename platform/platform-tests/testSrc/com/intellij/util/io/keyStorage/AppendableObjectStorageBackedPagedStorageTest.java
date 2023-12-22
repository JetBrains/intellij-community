// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.keyStorage;


import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.BlobStorageTestBase;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PageCacheUtils;
import com.intellij.util.io.StorageLockContext;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.Assume.assumeTrue;

public class AppendableObjectStorageBackedPagedStorageTest extends AppendableObjectStorageTestBase<String> {

  public static final int PAGE_SIZE = 1024;

  private final StorageLockContext context = new StorageLockContext(true, true, false);

  @BeforeClass
  public static void checkLockFreeEnabled() {
    assumeTrue("Can't test lock-free storage if LOCK_FREE_PAGE_CACHE_ENABLED=false",
               PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED);
  }

  @Override
  protected @NotNull AppendableObjectStorage<String> createStorage(Path path) throws IOException {
    return new AppendableStorageBackedByPagedStorageLockFree<>(
      path,
      context,
      PAGE_SIZE,
      /* valuesArePageAligned: */ false,
      EnumeratorStringDescriptor.INSTANCE,
      new ReentrantReadWriteLock()
    );
  }

  @Override
  protected @NotNull String generateValue() {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return BlobStorageTestBase.randomString(rnd, rnd.nextInt(128));
  }

  @Override
  protected String mutateValue(@NotNull String value) {
    char[] chars = value.toCharArray();
    if(chars.length == 0){
      return "abc";//guaranteed to not equal "" :)
    }
    int rndIndex = ThreadLocalRandom.current().nextInt(chars.length);
    chars[rndIndex]++;
    return new String(chars);
  }
}