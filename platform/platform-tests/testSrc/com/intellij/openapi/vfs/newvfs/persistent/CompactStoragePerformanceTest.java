// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.util.io.storage.Storage;
import com.intellij.util.io.storage.StorageTestBase;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class CompactStoragePerformanceTest extends StorageTestBase {
  @NotNull
  @Override
  protected Storage createStorage(@NotNull String fileName) throws IOException {
    return new CompactStorageTest.CompactStorage(fileName);
  }

  @Test
  public void testDeleteRemovesExtendedRecords() throws IOException {
    TIntArrayList recordsList = new TIntArrayList();
    // 60000 records of 40000 bytes each: exercise extra record creation
    int recordCount = 60000;
    for (int i = 0; i < recordCount; ++i) recordsList.add(CompactStorageTest.createTestRecord(myStorage));
    for (int r : recordsList.toNativeArray()) myStorage.deleteRecord(r);

    assertEquals(0, myStorage.getLiveRecordsCount());
  }
}