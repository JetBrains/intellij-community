// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;


import com.intellij.util.io.dev.mmapped.MMappedFileStorageFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Path;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE;

@RunWith(Parameterized.class)
public class PersistentFSRecordsStorageLockFreeOverMMappedFileTest
  extends PersistentFSRecordsStorageTestBase<PersistentFSRecordsLockFreeOverMMappedFile> {

  public static final int MAX_RECORDS_TO_INSERT = 1 << 22;

  @Parameterized.Parameters(name = "{index}: {0}")
  public static UpdateAPIMethod[] METHODS_TO_TEST() {
    return new UpdateAPIMethod[]{
      DEFAULT_API_UPDATE_METHOD,
      MODERN_API_UPDATE_METHOD
    };
  }

  public PersistentFSRecordsStorageLockFreeOverMMappedFileTest(UpdateAPIMethod updateMethodToTest) {
    super(MAX_RECORDS_TO_INSERT, updateMethodToTest);
  }


  @NotNull
  @Override
  protected PersistentFSRecordsLockFreeOverMMappedFile openStorage(Path storagePath) throws IOException {
    return MMappedFileStorageFactory.withDefaults()
      .pageSize(DEFAULT_MAPPED_CHUNK_SIZE)
      .wrapStorageSafely(storagePath, PersistentFSRecordsLockFreeOverMMappedFile::new);
  }
}