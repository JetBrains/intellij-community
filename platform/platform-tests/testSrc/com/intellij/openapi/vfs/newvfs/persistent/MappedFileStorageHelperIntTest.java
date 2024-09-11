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


public class MappedFileStorageHelperIntTest {
  private static final int ENOUGH_VALUES = 1 << 22;

  private static final int DEFAULT_VALUE = 0;

  private static final int VERSION = 1;

  private static final int FIELD_1_OFFSET_IN_ROW = 0;
  public static final int BYTES_PER_ROW = Integer.BYTES;

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
    return MappedFileStorageHelper.openHelperAndVerifyVersions(vfs, "testIntAttributeStorage", VERSION, BYTES_PER_ROW);
  }

  private static @NotNull MappedFileStorageHelper openWithoutCheckingVersions(@NotNull FSRecordsImpl vfs) throws IOException {
    return MappedFileStorageHelper.openHelper(vfs, "testIntAttributeStorage", BYTES_PER_ROW);
  }


  @Test
  public void openStorageWithSameName_JustReturnsSameInstance() throws IOException {
    String storageName = "name1";
    try (MappedFileStorageHelper storageHelper1 = MappedFileStorageHelper.openHelper(vfs, storageName, BYTES_PER_ROW)) {
      try (MappedFileStorageHelper storageHelper2 = MappedFileStorageHelper.openHelper(vfs, storageName, BYTES_PER_ROW)) {
        assertSame(
          storageHelper1,
          storageHelper2,
          "open storage with same name must return same instance");
      }
    }
  }

  @Test
  public void openStorageWithSameName_ButDifferentRowSize_Fails() throws IOException {
    String storageName = "name1";
    try (MappedFileStorageHelper storageHelper1 = MappedFileStorageHelper.openHelper(vfs, storageName, Integer.BYTES)) {
      try (MappedFileStorageHelper storageHelper2 = MappedFileStorageHelper.openHelper(vfs, storageName, Long.BYTES)) {
        fail("open storage with same name but different row size must fail");
      }
      catch (IllegalStateException e) {
        assertTrue(e.getMessage().contains("already registered"),
                   "open storage with same name but different row size must fail with message ~ 'already registered'");
      }
    }
  }

  @Test
  public void singleValue_CouldBeWrittenToStorage_AndReadBackAsIs() throws Exception {
    int fileId = vfs.createRecord();
    int valueToWrite = 42;
    storageHelper.writeIntField(fileId, FIELD_1_OFFSET_IN_ROW, valueToWrite);
    int valueReadBack = storageHelper.readIntField(fileId, FIELD_1_OFFSET_IN_ROW);
    assertEquals(valueToWrite, valueReadBack,
                 "attribute[#" + fileId + "] is unexpected");
  }

  @Test
  public void notYetWrittenValue_ReadsBackAsZero() throws Exception {
    int fileId = vfs.createRecord();
    int valueReadBack = storageHelper.readIntField(fileId, FIELD_1_OFFSET_IN_ROW);
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
      storageHelper.writeIntField(fileId, FIELD_1_OFFSET_IN_ROW, /*value: */ fileId);
    }

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      int valueReadBack = storageHelper.readIntField(fileId, FIELD_1_OFFSET_IN_ROW);
      assertEquals(fileId, valueReadBack,
                   "attribute[#" + fileId + "] is unexpected");
    }
  }

  @Test
  public void manyValues_NotWritten_ReadBackAsDefaultZero() throws Exception {
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      vfs.createRecord();
    }
    int maxAllocatedID = vfs.connection().records().maxAllocatedID();

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      int valueReadBack = storageHelper.readIntField(fileId, FIELD_1_OFFSET_IN_ROW);
      assertEquals(DEFAULT_VALUE, valueReadBack,
                   "attribute[#" + fileId + "] must be default(=0) since wasn't written before");
    }
  }

  @Test
  public void manyValues_CouldBeWrittenToStorage_AndReadBackAsIs_AfterReopen() throws Exception {
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      vfs.createRecord();
    }
    int maxAllocatedID = vfs.connection().records().maxAllocatedID();

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      storageHelper.writeIntField(fileId, FIELD_1_OFFSET_IN_ROW, /*value: */ fileId);
    }

    storageHelper.close();
    storageHelper = openAndEnsureVersionsMatch(vfs);

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      int valueReadBack = storageHelper.readIntField(fileId, FIELD_1_OFFSET_IN_ROW);
      assertEquals(fileId, valueReadBack,
                   "attribute[#" + fileId + "] is unexpected");
    }
  }

  @Test
  public void headers_CouldBeWrittenToStorage_AndReadBackAsIs_AfterReopen() throws Exception {
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
    storageHelper.writeIntField(fileId, FIELD_1_OFFSET_IN_ROW, /*value: */ attributeValue);

    storageHelper.close();
    storageHelper = openWithoutCheckingVersions(vfs);

    assertEquals(version, storageHelper.getVersion(),
                 "Version must be read back as-is");
    assertEquals(creationTag, storageHelper.getVFSCreationTag(),
                 "VFSCreationTag must be read back as-is");
    assertEquals(attributeValue, storageHelper.readIntField(fileId, FIELD_1_OFFSET_IN_ROW),
                 "Attribute value must be read back as-is");
  }

  @Test
  public void clearRecords_doesntTouchHeaders() throws Exception {
    long creationTag = Long.MAX_VALUE;
    int version = 42;
    storageHelper.setVersion(version);
    storageHelper.setVFSCreationTag(creationTag);

    storageHelper.clearRecords();
    assertEquals(version, storageHelper.getVersion(),
                 "Version must be read back as-is");
    assertEquals(creationTag, storageHelper.getVFSCreationTag(),
                 "VFSCreationTag must be read back as-is");
  }

  @Test
  public void clear_clearsHeadersAlso() throws Exception {
    long creationTag = Long.MAX_VALUE;
    int version = 42;
    storageHelper.setVersion(version);
    storageHelper.setVFSCreationTag(creationTag);

    storageHelper.clear();
    assertEquals(0, storageHelper.getVersion(),
                 "Version must be cleared");
    assertEquals(0, storageHelper.getVFSCreationTag(),
                 "VFSCreationTag must be cleared");
  }


  @Test
  public void ifVFSCreationTag_NotMatchedVFSCreationTimestamp_StorageContentIsCleared() throws Exception {
    storageHelper.setVersion(1);
    //Emulate 'wrong' vfsCreationTag:
    storageHelper.setVFSCreationTag(-1);
    int fileId = vfs.createRecord();
    storageHelper.writeIntField(fileId, FIELD_1_OFFSET_IN_ROW, /*value: */ Integer.MAX_VALUE);

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
                 storageHelper.readIntField(fileId, FIELD_1_OFFSET_IN_ROW),
                 "row[#" + fileId + "] must be default(=0) since VFSCreationTag was not matched");
  }
}