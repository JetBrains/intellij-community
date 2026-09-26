// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.mapped.MappedFileStorageHelper;
import com.intellij.platform.util.io.storages.StorageTestingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies short field access in {@link MappedFileStorageHelper}. */
public class MappedFileStorageHelperShortTest {
  private static final int ENOUGH_VALUES = 1 << 22;
  private static final short DEFAULT_VALUE = 0;
  private static final int VERSION = 1;

  private static final int FIELD_OFFSET_IN_ROW = 0;
  private static final int BYTES_PER_ROW = Short.BYTES;

  private FSRecordsImpl vfs;
  private MappedFileStorageHelper storageHelper;

  /** Opens an isolated VFS and its short field storage. */
  @BeforeEach
  public void setup(@TempDir Path vfsDir) throws IOException {
    vfs = FSRecordsImpl.connect(vfsDir);
    storageHelper = openAndEnsureVersionsMatch();
  }

  /** Closes all storage resources created by the test. */
  @AfterEach
  public void tearDown() throws Exception {
    storageHelper.closeAndClean();
    StorageTestingUtils.bestEffortToCloseAndClean(vfs);
  }

  /** Opens the short field storage and verifies its format version. */
  private MappedFileStorageHelper openAndEnsureVersionsMatch() throws IOException {
    return MappedFileStorageHelper.openHelperAndVerifyVersions(vfs, "testShortAttributeStorage", VERSION, BYTES_PER_ROW);
  }

  /** Verifies that the storage preserves all bits of a short value. */
  @Test
  public void singleValue_CouldBeWrittenToStorage_AndReadBackAsIs() throws Exception {
    var fileId = vfs.createRecord();
    var valueToWrite = Short.MIN_VALUE;

    storageHelper.writeShortField(fileId, FIELD_OFFSET_IN_ROW, valueToWrite);

    assertEquals(valueToWrite, storageHelper.readShortField(fileId, FIELD_OFFSET_IN_ROW),
                 "The short value must be read back as-is");
  }

  /** Verifies that an unwritten short field contains its default value. */
  @Test
  public void notYetWrittenValue_ReadsBackAsZero() throws Exception {
    var fileId = vfs.createRecord();

    assertEquals(DEFAULT_VALUE, storageHelper.readShortField(fileId, FIELD_OFFSET_IN_ROW),
                 "An unwritten short value must contain the default value");
  }

  /** Verifies short field access across multiple mapped pages. */
  @Test
  public void manyValues_CouldBeWrittenToStorage_AndReadBackAsIs() throws Exception {
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      vfs.createRecord();
    }
    var maxAllocatedID = vfs.connection().records().maxAllocatedID();

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      storageHelper.writeShortField(fileId, FIELD_OFFSET_IN_ROW, (short)-fileId);
    }

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      assertEquals((short)-fileId, storageHelper.readShortField(fileId, FIELD_OFFSET_IN_ROW),
                   "The short value must be read back as-is for file " + fileId);
    }
  }

  /** Verifies default short values across multiple mapped pages. */
  @Test
  public void manyValues_NotWritten_ReadBackAsDefaultZero() throws Exception {
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      vfs.createRecord();
    }
    var maxAllocatedID = vfs.connection().records().maxAllocatedID();

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      assertEquals(DEFAULT_VALUE, storageHelper.readShortField(fileId, FIELD_OFFSET_IN_ROW),
                   "An unwritten short value must contain the default value for file " + fileId);
    }
  }

  /** Verifies that reopening the storage preserves short values. */
  @Test
  public void manyValues_CouldBeWrittenToStorage_AndReadBackAsIs_AfterReopen() throws Exception {
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      vfs.createRecord();
    }
    var maxAllocatedID = vfs.connection().records().maxAllocatedID();

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      storageHelper.writeShortField(fileId, FIELD_OFFSET_IN_ROW, (short)-fileId);
    }

    storageHelper.close();
    storageHelper = openAndEnsureVersionsMatch();

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      assertEquals((short)-fileId, storageHelper.readShortField(fileId, FIELD_OFFSET_IN_ROW),
                   "The reopened storage must preserve the short value for file " + fileId);
    }
  }
}
