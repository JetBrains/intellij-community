// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.storage;

import com.intellij.util.io.StorageLockContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public class Storage extends AbstractStorage {
  public Storage(@NotNull Path path) throws IOException {
    super(path);
  }

  public Storage(@NotNull Path path, CapacityAllocationPolicy capacityAllocationPolicy) throws IOException {
    super(path, capacityAllocationPolicy);
  }

  @Override
  protected AbstractRecordsTable createRecordsTable(@NotNull StorageLockContext pool, @NotNull Path recordsFile) throws IOException {
    return new RecordsTable(recordsFile, pool);
  }

  public int createNewRecord() throws IOException {
    return withWriteLock(() -> {
      return myRecordsTable.createNewRecord();
    });
  }

  public void deleteRecord(int record) throws IOException {
    assert record > 0 : "recordId must be > 0";
    withWriteLock(() -> {
      doDeleteRecord(record);
    });
  }
}
