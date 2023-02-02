// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;


import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE;

public class PersistentFSRecordsStorageLockFreeOverMMappedFileTest
  extends PersistentFSRecordsStorageTestBase<PersistentFSRecordsLockFreeOverMMappedFile> {

  public static final int MAX_RECORDS_TO_INSERT = 1 << 22;


  public PersistentFSRecordsStorageLockFreeOverMMappedFileTest() { super(MAX_RECORDS_TO_INSERT); }


  @NotNull
  @Override
  protected PersistentFSRecordsLockFreeOverMMappedFile openStorage(final Path storagePath) throws IOException {
    return new PersistentFSRecordsLockFreeOverMMappedFile(storagePath,
                                                          DEFAULT_MAPPED_CHUNK_SIZE);
  }
}