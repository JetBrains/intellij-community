// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.keyStorage.MappedFileInputStream;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.io.InputStream;

public class MappedFileInputStreamTest extends InputStreamOverPagedStorageTestBase {

  private static final StorageLockContext CONTEXT = new StorageLockContext(true, true, false);

  private ResizeableMappedFile storage;

  @Before
  public void setUp() throws Exception {
    storage = new ResizeableMappedFile(
      temporaryFolder.newFile().toPath(),
      PAGE_SIZE,
      CONTEXT,
      PAGE_SIZE,
      false,
      false
    );
    storage.lockWrite();
  }

  @After
  public void tearDown() throws Exception {
    if (storage != null) {
      storage.close();
      storage.unlockWrite();
    }
  }




  @Override
  protected @NotNull InputStream streamOverStorage(long position, long limit) {
    return new MappedFileInputStream(storage, position, limit, false);
  }

  @Override
  protected byte[] writeRandomBytesToStorage(int bytesCount) throws IOException {
    byte[] bytesToWrite = randomBytes(bytesCount);
    storage.put(0, bytesToWrite, 0, bytesToWrite.length);
    return bytesToWrite;
  }
}