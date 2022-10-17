// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.io.ResizeableMappedFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsStorage.createFile;

/**
 */
public class PersistentFSSynchronizedRecordsStorageTest
  extends PersistentFSRecordsStorageTestBase<PersistentFSSynchronizedRecordsStorage> {


  public static final int MAX_RECORDS_TO_INSERT = 1 << 22;

  public PersistentFSSynchronizedRecordsStorageTest() {
    super(MAX_RECORDS_TO_INSERT);
  }

  @Override
  protected @NotNull PersistentFSSynchronizedRecordsStorage openStorage(final Path storagePath) throws IOException {
    final ResizeableMappedFile resizeableMappedFile = createFile(
      storagePath,
      PersistentFSSynchronizedRecordsStorage.RECORD_SIZE
    );
    final PersistentFSSynchronizedRecordsStorage storage = new PersistentFSSynchronizedRecordsStorage(resizeableMappedFile);
    //FIXME legacy implementation doesn't allocate space for header explicitly -- instead header is just 0-th record, and some
    //      code secretly calls .allocateRecord() on a fresh storage to reserve space for a header.
    //      Here I emulate this behavior, until it will be fixed in a regular way (i.e. storage will reserve space for
    //      header explicitly)
    if(storage.length() < PersistentFSHeaders.HEADER_SIZE) {
      storage.allocateRecord();
    }
    return storage;
  }
}
