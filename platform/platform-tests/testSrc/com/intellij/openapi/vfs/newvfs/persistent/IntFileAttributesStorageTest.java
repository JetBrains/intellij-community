// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;


public class IntFileAttributesStorageTest {
  private static final int ENOUGH_VALUES = 1 << 22;
  private static final int DEFAULT_VALUE = 0;

  private FSRecordsImpl vfs;
  private IntFileAttributesStorage attributeStorage;

  @BeforeEach
  public void setup(@TempDir Path vfsDir) throws IOException {
    vfs = FSRecordsImpl.connect(vfsDir);
    attributeStorage = openAndEnsureMatchVFS(vfs);
  }

  @AfterEach
  public void tearDown() throws Exception {
    vfs.dispose();
    attributeStorage.clear();
    attributeStorage.close();
  }

  private static @NotNull IntFileAttributesStorage openAndEnsureMatchVFS(@NotNull FSRecordsImpl vfs) throws IOException {
    return IntFileAttributesStorage.openAndEnsureMatchVFS(vfs, "testIntAttributeStorage");
  }

  private static @NotNull IntFileAttributesStorage openWithoutMatchingVFS(@NotNull FSRecordsImpl vfs) throws IOException {
    return IntFileAttributesStorage.openOrCreate(vfs, "testIntAttributeStorage");
  }

  @Test
  public void singleValue_CouldBeWrittenToAttributeStorage_AndReadBackAsIs() throws Exception {
    int fileId = vfs.createRecord();
    int valueToWrite = 42;
    attributeStorage.writeAttribute(fileId, valueToWrite);
    int valueReadBack = attributeStorage.readAttribute(fileId);
    assertEquals(valueToWrite, valueReadBack,
                 "attribute[#" + fileId + "] is unexpected");
  }

  @Test
  public void notYetWrittenAttributeValue_ReadsBackAsZero() throws Exception {
    int fileId = vfs.createRecord();
    int valueReadBack = attributeStorage.readAttribute(fileId);
    assertEquals(DEFAULT_VALUE,
                 valueReadBack,
                 "attribute[#" + fileId + "] is unexpected -- must be 0 (default)");
  }

  @Test
  public void manyValues_CouldBeWrittenToAttributeStorage_AndReadBackAsIs() throws Exception {
    for (int i = 0; i < ENOUGH_VALUES; i++) {
      int fileId = vfs.createRecord();
    }
    int maxAllocatedID = vfs.connection().getRecords().maxAllocatedID();

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      attributeStorage.writeAttribute(fileId, /*value: */ fileId);
    }

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      int valueReadBack = attributeStorage.readAttribute(fileId);
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
      int valueReadBack = attributeStorage.readAttribute(fileId);
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
      attributeStorage.writeAttribute(fileId, /*value: */ fileId);
    }

    attributeStorage.close();
    attributeStorage = openAndEnsureMatchVFS(vfs);

    for (int fileId = FSRecords.ROOT_FILE_ID; fileId <= maxAllocatedID; fileId++) {
      int valueReadBack = attributeStorage.readAttribute(fileId);
      assertEquals(fileId, valueReadBack,
                   "attribute[#" + fileId + "] is unexpected");
    }
  }

  @Test
  public void headers_CouldBeWrittenToAttributeStorage_AndReadBackAsIs_AfterReopen() throws Exception {
    long vfsTag = vfs.getCreationTimestamp();
    int version = 42;
    attributeStorage.setVersion(version);
    attributeStorage.setVFSCreationTag(vfsTag);

    attributeStorage.close();
    attributeStorage = openAndEnsureMatchVFS(vfs);

    assertEquals(version, attributeStorage.getVersion());
    assertEquals(vfsTag, attributeStorage.getVFSCreationTag());
  }

  @Test
  public void headersAndValues_DoesNotOverwriteEachOther() throws Exception {
    long creationTag = Long.MAX_VALUE;
    int version = 42;
    attributeStorage.setVersion(version);
    attributeStorage.setVFSCreationTag(creationTag);

    int attributeValue = Integer.MAX_VALUE;//all bits
    int fileId = vfs.createRecord();
    attributeStorage.writeAttribute(fileId, attributeValue);

    attributeStorage.close();
    attributeStorage = openWithoutMatchingVFS(vfs);

    assertEquals(version, attributeStorage.getVersion(),
                 "Version must be read back as-is");
    assertEquals(creationTag, attributeStorage.getVFSCreationTag(),
                 "VFSCreationTag must be read back as-is");
    assertEquals(attributeValue, attributeStorage.readAttribute(fileId),
                 "Attribute value must be read back as-is");
  }

  @Test
  public void ifVFSCreationTag_NotMatchedVFSCreationTimestamp_StorageContentIsCleared() throws Exception {
    attributeStorage.setVersion(1);
    //Emulate 'wrong' vfsCreationTag:
    attributeStorage.setVFSCreationTag(-1);
    int fileId = vfs.createRecord();
    attributeStorage.writeAttribute(fileId, Integer.MAX_VALUE);

    attributeStorage.close();
    attributeStorage = openAndEnsureMatchVFS(vfs);

    //since vfsCreationTag != vfs.getCreationTimestamp() => attributeStorage
    // must be created anew, i.e. empty, and with all the values default
    // (i.e. all 0 except for vfsCreationTag which is = vfs.getCreationTimestamp )
    assertEquals(DEFAULT_VALUE, attributeStorage.getVersion(),
                 "Storage version is default(=0) since VFSCreationTag was not matched");
    assertEquals(vfs.getCreationTimestamp(),
                 attributeStorage.getVFSCreationTag(),
                 "Storage VFSCreationTag must be == current VFS.getCreationTimestamp()");
    assertEquals(DEFAULT_VALUE,
                 attributeStorage.readAttribute(fileId),
                 "attribute[#" + fileId + "] must be default(=0) since VFSCreationTag was not matched");
  }
}