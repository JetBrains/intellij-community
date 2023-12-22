// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.keyStorage;


import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.BlobStorageTestBase;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.StorageLockContext;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;

public class AppendableObjectStorageBackedByResizableMappedFileTest extends AppendableObjectStorageTestBase<String> {

  public static final int PAGE_SIZE = 1024;

  private final StorageLockContext context = new StorageLockContext(true, true, false);

  @Override
  protected @NotNull AppendableObjectStorage<String> createStorage(Path path) throws IOException {
    return new AppendableStorageBackedByResizableMappedFile<>(
      path,
      PAGE_SIZE,
      context,
      PAGE_SIZE,
      /* valuesArePageAligned: */ false,
      EnumeratorStringDescriptor.INSTANCE
    );
  }

  @Test
  public void processAll_failsIfCalledUnderReadLock() throws IOException {
    appendableStorage.lockRead();
    try {
      appendableStorage.processAll((valueId, value) -> {
        return true;
      });
      fail(".processAll() must fail under readLock");
    }
    catch (IllegalStateException e) {
      assertTrue(
        "Error message must be something ~ 'readLock must NOT be held', but [" + e.getMessage() + "]",
        e.getMessage().contains("readLock must NOT")
      );
    }
    finally {
      appendableStorage.unlockRead();
    }
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