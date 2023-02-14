// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;


import com.intellij.util.io.StorageLockContext;
import org.jetbrains.annotations.NotNull;
import org.junit.After;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsOverLockFreePagedStorage.RECORD_SIZE_IN_BYTES;

public class PersistentFSRecordsStorageLockFreeOverMMappedFileTest
  extends PersistentFSRecordsStorageTestBase<PersistentFSRecordsLockFreeOverMMappedFile> {

  public static final int MAX_RECORDS_TO_INSERT = 1 << 22;


  public PersistentFSRecordsStorageLockFreeOverMMappedFileTest() { super(MAX_RECORDS_TO_INSERT); }

  private StorageLockContext storageContext;

  @NotNull
  @Override
  protected PersistentFSRecordsLockFreeOverMMappedFile openStorage(final Path storagePath) throws IOException {
    final int pageSize;
    final boolean nativeBytesOrder;
    try (var file = PersistentFSRecordsStorageFactory.openRMappedFile(storagePath, RECORD_SIZE_IN_BYTES)) {
      storageContext = file.getStorageLockContext();
      pageSize = file.getPagedFileStorage().getPageSize();
      nativeBytesOrder = file.isNativeBytesOrder();
    }
    return new PersistentFSRecordsLockFreeOverMMappedFile(storagePath, PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE);
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }
}