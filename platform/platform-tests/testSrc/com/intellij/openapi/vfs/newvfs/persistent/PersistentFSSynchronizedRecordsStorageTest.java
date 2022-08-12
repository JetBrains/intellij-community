// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.io.ResizeableMappedFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsStorage.createFile;

/**
 * FIXME type something meaningful here
 */
public class PersistentFSSynchronizedRecordsStorageTest
  extends PersistentFSRecordsStorageTestBase<PersistentFSSynchronizedRecordsStorage> {


  public static final int MAX_RECORDS_TO_INSERT = 1 << 22;

  public PersistentFSSynchronizedRecordsStorageTest() {
    super(MAX_RECORDS_TO_INSERT);
  }

  @Override
  protected @NotNull PersistentFSSynchronizedRecordsStorage openStorage(final File storageFile,
                                                                        final int maxRecordsToInsert) throws IOException {
    final ResizeableMappedFile resizeableMappedFile = createFile(
      storageFile.toPath(),
      PersistentFSSynchronizedRecordsStorage.RECORD_SIZE
    );
    return new PersistentFSSynchronizedRecordsStorage(resizeableMappedFile);
    //FSRecords.connect();
  }
}
