/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.history.integration;

import com.intellij.history.core.LocalHistoryStorage;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.io.storage.AbstractStorage;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LocalHistoryStorageTest extends IntegrationTestCase {
  private LocalHistoryStorage myStorage;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myStorage = new LocalHistoryStorage(myRoot.getPath() + "/storage");
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(myStorage);
    super.tearDown();
  }

  public void testBasic() throws Exception {
    assertFirstAndLast(0, 0);

    int r1 = createRecord();
    int r2 = createRecord();

    assertFirstAndLast(r1, r2);
    assertRecord(r2, r1, 0);
    assertRecord(r1, 0, r2);
  }

  public void testWritingAfterClose() throws Exception {
    createRecord();
    Disposer.dispose(myStorage);

    try {
      createRecord();
    }
    catch (AssertionError e) {
      return;
    }
    fail("should have thrown exception");
  }

  public void testTrimming() throws Exception {
    int r1 = createRecord();
    int r2 = createRecord();
    int r3 = createRecord();
    int r4 = createRecord();

    assertFirstAndLast(r1, r4);
    assertRecord(r1, 0, r2);
    assertRecord(r2, r1, r3);
    assertRecord(r3, r2, r4);
    assertRecord(r4, r3, 0);

    myStorage.deleteRecordsUpTo(r2);

    assertFirstAndLast(r3, r4);
    assertRecord(r3, 0, r4);
    assertRecord(r4, r3, 0);

    myStorage.deleteRecordsUpTo(r4);

    assertFirstAndLast(0, 0);
  }

  public void testReopening() throws Exception {
    int r1 = createRecord();
    int r2 = createRecord();
    int r3 = createRecord();
    int r4 = createRecord();

    myStorage.deleteRecordsUpTo(r2);

    Disposer.dispose(myStorage);
    myStorage = new LocalHistoryStorage(myRoot.getPath() + "/storage");

    assertFirstAndLast(r3, r4);
    assertRecord(r3, 0, r4);
    assertRecord(r4, r3, 0);

    myStorage.deleteRecordsUpTo(r3);

    Disposer.dispose(myStorage);
    myStorage = new LocalHistoryStorage(myRoot.getPath() + "/storage");

    assertFirstAndLast(r4, r4);
    assertRecord(r4, 0, 0);

    int r5 = createRecord();

    Disposer.dispose(myStorage);
    myStorage = new LocalHistoryStorage(myRoot.getPath() + "/storage");

    assertFirstAndLast(r4, r5);
    assertRecord(r4, 0, r5);
    assertRecord(r5, r4, 0);
  }

  public void testWritingChangesOfDifferentSize() throws Exception {
    final int MAX = 100;
    List<Integer> records = new ArrayList<>(MAX);
    for (int i = 0; i < MAX; i++) {
      if (i > MAX / 2) {
        myStorage.deleteRecordsUpTo(records.get(records.size() - MAX / 2));
      }
      records.add(createRecord(i*50));
    }
    
    assertFirstAndLast(records.get(records.size() - MAX / 2), records.get(records.size() - 1));
  }

  private int createRecord() throws IOException {
    return createRecord(1000);
  }

  private int createRecord(int size) throws IOException {
    int r = myStorage.createNextRecord();
    AbstractStorage.StorageDataOutput s = myStorage.writeStream(r, true);
    for (int i = 0; i < size; i++) {
      s.writeInt(r);
    }
    s.close();
    return r;
  }

  private void assertFirstAndLast(int first, int last) {
    assertEquals(first, myStorage.getFirstRecord());
    assertEquals(last, myStorage.getLastRecord());
  }

  private void assertRecord(int id, int prev, int next) throws IOException {
    assertEquals(prev, myStorage.getPrevRecord(id));
    assertEquals(next, myStorage.getNextRecord(id));
    DataInputStream s = myStorage.readStream(id);
    try {
      for (int i = 0; i < 1000; i++) {
        assertEquals(id, s.readInt());
      }
    }
    finally {
      s.close();
    }
  }
}
