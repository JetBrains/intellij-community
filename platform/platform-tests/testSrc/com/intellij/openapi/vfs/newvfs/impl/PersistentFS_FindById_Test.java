// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@TestApplication
public class PersistentFS_FindById_Test {

  @Test
  void findFileById_findsFileForEveryValidFileId_inVFS() {
    PersistentFSImpl pFS = (PersistentFSImpl)PersistentFS.getInstance();
    FSRecordsImpl fsRecords = pFS.peer();
    int maxAllocatedID = fsRecords.connection().records().maxAllocatedID();
    for (int fileId = FSRecords.ROOT_FILE_ID + 1; fileId <= maxAllocatedID; fileId++) {
      NewVirtualFile file = pFS.findFileById(fileId);
      if (fsRecords.isDeleted(fileId)) {
        assertNull(file, "findFileById(" + fileId + ") must be null for deleted files");
      }
      else {
        assertNotNull(file, "findFileById(" + fileId + ") must be !null");
        assertEquals(fileId, file.getId(), "findFileById(" + fileId + ") must return file with same id: " + file);
      }
    }
  }
}
