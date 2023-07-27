// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.dev.MappedFileStorageHelper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class MappedFileStorageHelperTest {
  private static final int ENOUGH_VALUES = 1 << 22;

  private static final int DEFAULT_VALUE = 0;

  private static final int VERSION = 1;

  private static final int FIELD_OFFSET_IN_ROW = 0;

  private FSRecordsImpl vfs;
  private MappedFileStorageHelper storageHelper;

  @BeforeEach
  public void setup(@TempDir Path vfsDir) throws IOException {
    vfs = FSRecordsImpl.connect(vfsDir);

    storageHelper = openAndEnsureVersionsMatch(vfs);
  }

  @AfterEach
  public void tearDown() throws Exception {
    vfs.dispose();
    storageHelper.clear();
    storageHelper.close();
  }

  private static @NotNull MappedFileStorageHelper openAndEnsureVersionsMatch(@NotNull FSRecordsImpl vfs) throws IOException {
    return MappedFileStorageHelper.openHelperAndVerifyVersions(vfs, "testIntAttributeStorage", VERSION, Integer.BYTES);
  }

  private static @NotNull MappedFileStorageHelper openWithoutCheckingVersions(@NotNull FSRecordsImpl vfs) throws IOException {
    return MappedFileStorageHelper.openHelper(vfs, "testIntAttributeStorage", Integer.BYTES);
  }

  @Test
  public void singleValue_CouldBeWrittenToAttributeStorage_AndReadBackAsIs() throws Exception {
    int fileId = vfs.createRecord();
    int valueToWrite = 42;
    storageHelper.writeIntField(fileId, FIELD_OFFSET_IN_ROW, valueToWrite);
    int valueReadBack = storageHelper.readIntField(fileId, FIELD_OFFSET_IN_ROW);
    assertEquals(valueToWrite, valueReadBack,
                 "attribute[#" + fileId + "] is unexpected");
  }

  @Test
  public void notYetWrittenAttributeValue_ReadsBackAsZero() throws Exception {
    int fileId = vfs.createRecord();
    int valueReadBack = storageHelper.readIntField(fileId, FIELD_OFFSET_IN_ROW);
    assertEquals(DEFAULT_VALUE,
                 valueReadBack,
                 "attribute[#" + fileId + "] is unexpected -- must be 0 (default)");
  }

  @Test
  public void manyValues_CouldBeWrittenToAttributeStorage_AndReadBackAsIs() throws Exception {
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      vfs.createRecord();
    }
    int maxAllocatedID = vfs.connection().getRecords().maxAllocatedID();

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      storageHelper.writeIntField(fileId, FIELD_OFFSET_IN_ROW, /*value: */ fileId);
    }

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      int valueReadBack = storageHelper.readIntField(fileId, FIELD_OFFSET_IN_ROW);
      assertEquals(fileId, valueReadBack,
                   "attribute[#" + fileId + "] is unexpected");
    }
  }

  @Test
  public void manyValues_NotWritten_ReadBackAsDefaultZero() throws Exception {
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      vfs.createRecord();
    }
    int maxAllocatedID = vfs.connection().getRecords().maxAllocatedID();

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      int valueReadBack = storageHelper.readIntField(fileId, FIELD_OFFSET_IN_ROW);
      assertEquals(DEFAULT_VALUE, valueReadBack,
                   "attribute[#" + fileId + "] must be default(=0) since wasn't written before");
    }
  }


  @Test
  public void manyValues_CouldBeWrittenToAttributeStorage_AndReadBackAsIs_AfterReopen() throws Exception {
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      vfs.createRecord();
    }
    int maxAllocatedID = vfs.connection().getRecords().maxAllocatedID();

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      storageHelper.writeIntField(fileId, FIELD_OFFSET_IN_ROW, /*value: */ fileId);
    }

    storageHelper.close();
    storageHelper = openAndEnsureVersionsMatch(vfs);

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      int valueReadBack = storageHelper.readIntField(fileId, FIELD_OFFSET_IN_ROW);
      assertEquals(fileId, valueReadBack,
                   "attribute[#" + fileId + "] is unexpected");
    }
  }

  @Test
  public void headers_CouldBeWrittenToAttributeStorage_AndReadBackAsIs_AfterReopen() throws Exception {
    long vfsTag = System.currentTimeMillis();
    int version = 42;
    storageHelper.setVersion(version);
    storageHelper.setVFSCreationTag(vfsTag);

    storageHelper.close();
    storageHelper = openWithoutCheckingVersions(vfs);

    assertEquals(version, storageHelper.getVersion());
    assertEquals(vfsTag, storageHelper.getVFSCreationTag());
  }

  @Test
  public void headersAndValues_DoesNotOverwriteEachOther() throws Exception {
    long creationTag = Long.MAX_VALUE;
    int version = 42;
    storageHelper.setVersion(version);
    storageHelper.setVFSCreationTag(creationTag);

    int attributeValue = Integer.MAX_VALUE;//all bits
    int fileId = vfs.createRecord();
    storageHelper.writeIntField(fileId, FIELD_OFFSET_IN_ROW, /*value: */ attributeValue);

    storageHelper.close();
    storageHelper = openWithoutCheckingVersions(vfs);

    assertEquals(version, storageHelper.getVersion(),
                 "Version must be read back as-is");
    assertEquals(creationTag, storageHelper.getVFSCreationTag(),
                 "VFSCreationTag must be read back as-is");
    assertEquals(attributeValue, storageHelper.readIntField(fileId, FIELD_OFFSET_IN_ROW),
                 "Attribute value must be read back as-is");
  }

  @Test
  public void ifVFSCreationTag_NotMatchedVFSCreationTimestamp_StorageContentIsCleared() throws Exception {
    storageHelper.setVersion(1);
    //Emulate 'wrong' vfsCreationTag:
    storageHelper.setVFSCreationTag(-1);
    int fileId = vfs.createRecord();
    storageHelper.writeIntField(fileId, FIELD_OFFSET_IN_ROW, /*value: */ Integer.MAX_VALUE);

    storageHelper.close();
    storageHelper = openAndEnsureVersionsMatch(vfs);

    //since vfsCreationTag != vfs.getCreationTimestamp() => attributeStorage
    // must be created anew, i.e. empty, and with all the values default
    // (i.e. all 0 except for vfsCreationTag which is = vfs.getCreationTimestamp )
    assertEquals(VERSION, storageHelper.getVersion(),
                 "Storage version must be " + VERSION + " since VFSCreationTag was not matched");
    assertEquals(vfs.getCreationTimestamp(),
                 storageHelper.getVFSCreationTag(),
                 "Storage VFSCreationTag must be == current VFS.getCreationTimestamp()");
    assertEquals(DEFAULT_VALUE,
                 storageHelper.readIntField(fileId, FIELD_OFFSET_IN_ROW),
                 "row[#" + fileId + "] must be default(=0) since VFSCreationTag was not matched");
  }
}