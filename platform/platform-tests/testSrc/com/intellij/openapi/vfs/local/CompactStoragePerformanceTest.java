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

import com.intellij.util.io.storage.Storage;
import com.intellij.util.io.storage.StorageTestBase;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class CompactStoragePerformanceTest extends StorageTestBase {
  @NotNull
  @Override
  protected Storage createStorage(String fileName) throws IOException {
    return new CompactStorageTest.CompactStorage(fileName);
  }

  public void testDeleteRemovesExtendedRecords() throws IOException {
    TIntArrayList recordsList = new TIntArrayList();
    // 60000 records of 40000 bytes each: exercise extra record creation
    int recordCount = 60000;
    for(int i = 0; i < recordCount; ++i) recordsList.add(CompactStorageTest.createTestRecord(myStorage));
    for (int r: recordsList.toNativeArray()) myStorage.deleteRecord(r);

    assertEquals(0, myStorage.getLiveRecordsCount());
  }
}