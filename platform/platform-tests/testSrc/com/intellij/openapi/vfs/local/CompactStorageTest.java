/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.newvfs.persistent.CompactRecordsTable;
import com.intellij.util.io.PagePool;
import com.intellij.util.io.storage.AbstractRecordsTable;
import com.intellij.util.io.storage.Storage;
import com.intellij.util.io.storage.StorageTest;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

public class CompactStorageTest extends StorageTest {
  @NotNull
  @Override
  protected Storage createStorage(String fileName) throws IOException {
    return new Storage(fileName) {
      @Override
      protected AbstractRecordsTable createRecordsTable(PagePool pool, File recordsFile) throws IOException {
        return new CompactRecordsTable(recordsFile, pool, false);
      }
    };
  }

  public void testDeleteRemovesExtendedRecords() throws IOException {
    TIntArrayList recordsList = new TIntArrayList();
    for(int i = 0; i < 60000; ++i) recordsList.add(createTestRecord());
    for (int r: recordsList.toNativeArray()) myStorage.deleteRecord(r);

    Disposer.dispose(myStorage);
    myStorage = createStorage(getFileName());
    assertEquals(myStorage.getLiveRecordsCount(), 0);
  }

  protected int createTestRecord() throws IOException {
    final int r = myStorage.createNewRecord();

    DataOutputStream out = new DataOutputStream(myStorage.appendStream(r));
    for (int i = 0; i < 10000; i++) {
      out.writeInt(i);
    }

    out.close();
    return r;
  }
}