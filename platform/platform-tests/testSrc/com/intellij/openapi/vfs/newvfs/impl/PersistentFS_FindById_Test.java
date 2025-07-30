// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

@TestApplication
public class PersistentFS_FindById_Test {

  private static final int MAX_FILES_TO_TRIAL = 100_000;

  @Test
  void findFileById_findsFileForEveryValidFileId_inVFS() {
    PersistentFSImpl pFS = (PersistentFSImpl)PersistentFS.getInstance();
    FSRecordsImpl fsRecords = pFS.peer();
    int maxAllocatedID = fsRecords.connection().records().maxAllocatedID();
    pFS.clearIdCache();

    int maxFilesToCheck = Math.min(MAX_FILES_TO_TRIAL, maxAllocatedID);
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < maxFilesToCheck; i++) {
      //generate fileId randomly, so that all combinations of cached/uncached parents are realised:
      int fileId = rnd.nextInt(FSRecords.ROOT_FILE_ID + 1, maxAllocatedID + 1);
      NewVirtualFile file = pFS.findFileById(fileId);
      if (fsRecords.isDeleted(fileId)) {
        assertNull(file, "findFileById(" + fileId + ") must be null for deleted files");
      }
      else {
        assertNotNull(
          file,
          "findFileById(" + fileId + ") must be !null"
        );
        assertEquals(
          fileId, file.getId(),
          "findFileById(" + fileId + ") must return file with same id: " + file
        );

        NewVirtualFile parent = file.getParent();
        if (parent != null) {//i.e.: not root
          Collection<VirtualFile> cachedChildren = parent.getCachedChildren();
          assertTrue(
            cachedChildren.contains(file),
            "Parent(=" + parent + ") must cache children before it is resolved: cachedChildren: " + cachedChildren + ", child: " + file
          );
        }
      }
    }
  }
}
