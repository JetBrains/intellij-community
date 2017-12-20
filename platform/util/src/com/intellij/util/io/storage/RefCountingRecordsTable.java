/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io.storage;

import com.intellij.util.io.PagePool;

import java.io.File;
import java.io.IOException;

class RefCountingRecordsTable extends AbstractRecordsTable {
  private static final int VERSION = 1;

  private static final int REF_COUNT_OFFSET = DEFAULT_RECORD_SIZE;
  private static final int RECORD_SIZE = REF_COUNT_OFFSET + 4;

  private static final byte[] ZEROES = new byte[RECORD_SIZE];

  public RefCountingRecordsTable(File recordsFile, PagePool pool) throws IOException {
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

  public void incRefCount(int record) {
    markDirty();

    int offset = getOffset(record, REF_COUNT_OFFSET);
    myStorage.putInt(offset, myStorage.getInt(offset) + 1);
  }

  public boolean decRefCount(int record) {
    markDirty();

    int offset = getOffset(record, REF_COUNT_OFFSET);
    int count = myStorage.getInt(offset);
    assert count > 0;
    count--;
    myStorage.putInt(offset, count);
    return count == 0;
  }

  public int getRefCount(int record) {
    return myStorage.getInt(getOffset(record, REF_COUNT_OFFSET));
  }
}
