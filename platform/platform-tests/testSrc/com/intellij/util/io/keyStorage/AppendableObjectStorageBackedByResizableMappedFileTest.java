// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.keyStorage;


import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.BlobStorageTestBase;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.StorageLockContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

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

  @Override
  protected @NotNull String generateValue() {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return BlobStorageTestBase.randomString(rnd, rnd.nextInt(128));
  }
}