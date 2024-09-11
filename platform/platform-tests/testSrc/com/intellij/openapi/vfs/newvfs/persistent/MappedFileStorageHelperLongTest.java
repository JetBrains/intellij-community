// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.mapped.MappedFileStorageHelper;
import com.intellij.platform.util.io.storages.StorageTestingUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.*;


public class MappedFileStorageHelperLongTest {
  private static final int ENOUGH_VALUES = 1 << 22;

  private static final int DEFAULT_VALUE = 0;

  private static final int VERSION = 1;

  private static final int FIELD_LONG_OFFSET_IN_ROW = 0;
  private static final int BYTES_PER_ROW = Long.BYTES;

  private FSRecordsImpl vfs;
  private MappedFileStorageHelper storageHelper;

  private Map<Path, MappedFileStorageHelper> registeredStoragesBefore;

  @BeforeEach
  public void setup(@TempDir Path vfsDir) throws IOException {
    registeredStoragesBefore = MappedFileStorageHelper.registeredStorages();

    vfs = FSRecordsImpl.connect(vfsDir);

    storageHelper = openAndEnsureVersionsMatch(vfs);
  }

  @AfterEach
  public void tearDown() throws Exception {
    //Ideally, storage file is removed after each test. But if mapped file
    // can't be removed (a thing for Win) -- at least clear it's content so
    // next test see it as empty file:
    storageHelper.closeAndClean();
    StorageTestingUtils.bestEffortToCloseAndClean(vfs);


    //RC: Can't just check for .isEmpty(): if running in the same process with other tests -- could be storages
    //    registered by them
    Map<Path, MappedFileStorageHelper> registeredStoragesAfter = MappedFileStorageHelper.registeredStorages();
    assertEquals(
      registeredStoragesBefore,
      registeredStoragesAfter,
      "All storages opened during the test -- must be closed and de-registered during VFS close: \n" +
      "before:" + registeredStoragesAfter.keySet().stream().map(p -> p.toString())
        .collect(joining("\n\t", "\n", "\n")) +
      "\nafter:" + registeredStoragesAfter.keySet().stream().map(p -> p.toString())
        .collect(joining("\n\t", "\n", "\n"))
    );
  }

  private static @NotNull MappedFileStorageHelper openAndEnsureVersionsMatch(@NotNull FSRecordsImpl vfs) throws IOException {
    return MappedFileStorageHelper.openHelperAndVerifyVersions(vfs, "testLongAttributeStorage", VERSION, BYTES_PER_ROW);
  }

  @Test
  public void singleValue_CouldBeWrittenToStorage_AndReadBackAsIs() throws Exception {
    int fileId = vfs.createRecord();
    long valueToWrite = -42L;
    storageHelper.writeLongField(fileId, FIELD_LONG_OFFSET_IN_ROW, valueToWrite);
    long valueReadBack = storageHelper.readLongField(fileId, FIELD_LONG_OFFSET_IN_ROW);
    assertEquals(valueToWrite, valueReadBack,
                 "attribute[#" + fileId + "] is unexpected");
  }

  @Test
  public void notYetWrittenValue_ReadsBackAsZero() throws Exception {
    int fileId = vfs.createRecord();
    long valueReadBack = storageHelper.readLongField(fileId, FIELD_LONG_OFFSET_IN_ROW);
    assertEquals(DEFAULT_VALUE,
                 valueReadBack,
                 "attribute[#" + fileId + "] is unexpected -- must be 0 (default)");
  }

  @Test
  public void manyValues_CouldBeWrittenToStorage_AndReadBackAsIs() throws Exception {
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      vfs.createRecord();
    }
    int maxAllocatedID = vfs.connection().records().maxAllocatedID();

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      storageHelper.writeLongField(fileId, FIELD_LONG_OFFSET_IN_ROW, /*value: */ -fileId);
    }

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      long valueReadBack = storageHelper.readLongField(fileId, FIELD_LONG_OFFSET_IN_ROW);
      assertEquals(-fileId, valueReadBack,
                   "value[#" + fileId + "] is unexpected");
    }
  }

  @Test
  public void manyValues_NotWritten_ReadBackAsDefaultZero() throws Exception {
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      vfs.createRecord();
    }
    int maxAllocatedID = vfs.connection().records().maxAllocatedID();

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      long valueRead = storageHelper.readLongField(fileId, FIELD_LONG_OFFSET_IN_ROW);
      assertEquals(DEFAULT_VALUE, valueRead,
                   "value[#" + fileId + "] must be default(=0) since wasn't written before");
    }
  }

  @Test
  public void manyValues_CouldBeWrittenToStorage_AndReadBackAsIs_AfterReopen() throws Exception {
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      vfs.createRecord();
    }
    int maxAllocatedID = vfs.connection().records().maxAllocatedID();

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      storageHelper.writeLongField(fileId, FIELD_LONG_OFFSET_IN_ROW, /*value: */ -fileId);
    }

    storageHelper.close();
    storageHelper = openAndEnsureVersionsMatch(vfs);

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      long valueReadBack = storageHelper.readLongField(fileId, FIELD_LONG_OFFSET_IN_ROW);
      assertEquals(-fileId, valueReadBack,
                   "value[#" + fileId + "] is unexpected");
    }
  }

}