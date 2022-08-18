// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io.storage;

import com.intellij.util.io.StorageLockContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

final class RefCountingRecordsTable extends AbstractRecordsTable {
  private static final int VERSION = 1;

  private static final int REF_COUNT_OFFSET = DEFAULT_RECORD_SIZE;
  private static final int RECORD_SIZE = REF_COUNT_OFFSET + 4;

  private static final byte[] ZEROES = new byte[RECORD_SIZE];

  RefCountingRecordsTable(@NotNull Path recordsFile, StorageLockContext pool) throws IOException {
    super(recordsFile, pool);
  }

  @Override
  protected int getImplVersion() {
    return VERSION;
  }

  @Override
  protected int getRecordSize() {
    return RECORD_SIZE;
  }

  @Override
  protected byte[] getZeros() {
    return ZEROES;
  }

  public void incRefCount(int record) throws IOException {
    markDirty();

    int offset = getOffset(record, REF_COUNT_OFFSET);
    myStorage.putInt(offset, myStorage.getInt(offset) + 1);
  }

  public boolean decRefCount(int record) throws IOException {
    markDirty();

    int offset = getOffset(record, REF_COUNT_OFFSET);
    int count = myStorage.getInt(offset);
    assert count > 0;
    count--;
    myStorage.putInt(offset, count);
    return count == 0;
  }

  public int getRefCount(int record) throws IOException {
    return myStorage.getInt(getOffset(record, REF_COUNT_OFFSET));
  }
}
