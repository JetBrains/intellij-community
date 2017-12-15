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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    final int currentRefCount = myStorage.getInt(offset);
    addDebugHistoryEntry(record, "incRefCount: " + currentRefCount + " -> " + (currentRefCount + 1));
    myStorage.putInt(offset, currentRefCount + 1);
  }

  private Map<Integer, List<Throwable>> myDebugHistory = new ConcurrentHashMap<Integer, List<Throwable>>();
  // Report debug info only once to limit log size.
  private static boolean ourWasObjectHistoryReported = false;

  public boolean decRefCount(int record) {
    markDirty();

    int offset = getOffset(record, REF_COUNT_OFFSET);
    int count = myStorage.getInt(offset);
    addDebugHistoryEntry(record, "decRefCount: " + count + " -> " + (count - 1));
    //assert count > 0; // TODO: Re-enable the assert and remove debug code: b/70639656
    if (count <= 0) {
      if (!ourWasObjectHistoryReported) {
        ourWasObjectHistoryReported = true;
        dumpObjectDebugHistory(record);
      }
    } else {
      count--;
      myStorage.putInt(offset, count);
    }
    return count == 0;
  }

  private void dumpObjectDebugHistory(int recordId) {
    System.out.println("Illegal reference count for record: " + recordId);
    if (myStorage != null) {
      System.out.println("myStorage.isDisposed() = " + myStorage.isDisposed());
      if (!myStorage.isDisposed()) {
        System.out.println("myStorage.getFile() = " + myStorage.getFile());
      }
    }
    System.out.println("Reference counting history:");
    final List<Throwable> throwableList = myDebugHistory.get(recordId);
    synchronized (throwableList) {
      for (Throwable entry : throwableList) {
        entry.printStackTrace(System.out);
      }
    }
  }

  public int getRefCount(int record) {
    return myStorage.getInt(getOffset(record, REF_COUNT_OFFSET));
  }

  private void addDebugHistoryEntry(int recordId, String message) {
    List<Throwable> history = myDebugHistory.get(recordId);
    if (history == null) {
      history = new ArrayList<Throwable>();
      myDebugHistory.put(recordId, history);
    }
    synchronized (history) {
      history.add(new DebugEntryException(message));
    }
  }

  private static class DebugEntryException extends Exception {
    public DebugEntryException(String message) {
      super(message);
    }
  }
}
