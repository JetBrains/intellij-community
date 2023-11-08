// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.util.io.DataOutputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

public class FSRecordsImplTest {


  private FSRecordsImpl vfs;

  @Test
  public void allocatedRecordId_CouldBeAlwaysWritten_EvenInMultiThreadedEnv() throws Exception {
    //RC: there are EA reports with 'fileId ... outside of allocated range ...' exception
    //    _just after recordId was allocated_. So the test checks there are no concurrency errors
    //    that could leads to that:
    int CPUs = Runtime.getRuntime().availableProcessors();
    int recordsPerThread = 1_000_000;
    ExecutorService pool = Executors.newFixedThreadPool(CPUs);
    try {
      FileAttributes attributes = new FileAttributes(true, false, false, true, 1, 1, true);
      Callable<Object> insertingRecordsTask = () -> {
        for (int i = 0; i < recordsPerThread; i++) {
          int fileId = vfs.createRecord();
          vfs.updateRecordFields(fileId, 1, attributes, "file_" + i, true);
        }
        return null;
      };
      List<Future<Object>> futures = IntStream.range(0, CPUs)
        .mapToObj(i -> insertingRecordsTask)
        .map(pool::submit)
        .toList();
      for (Future<Object> future : futures) {
        future.get();//give a chance to deliver exception
      }
    }
    finally {
      pool.shutdown();
      pool.awaitTermination(15, SECONDS);
    }
  }

  @Test
  public void writtenFileContentIsDeduplicated_onlyUniqueContentsAreStored() throws IOException {
    int contentsToInsert = 1024;
    int uniqueContentsCount = 4;
    for (int i = 0; i < contentsToInsert; i++) {
      int fileId = vfs.createRecord();
      String content = "testContent_" + (i % uniqueContentsCount);
      try (DataOutputStream stream = vfs.writeContent(fileId, /*fixed: */false)) {
        stream.writeUTF(content);
      }
    }

    assertEquals(
      uniqueContentsCount,
      vfs.connection().getContents().getRecordsCount(),
      "Only " + uniqueContentsCount + " unique contents should be stored"
    );
  }

  @Test
  public void writtenFileContent_couldBeReadBackAsIs() throws IOException {
    int contentsToInsert = 1024;
    int uniqueContentsCount = 4;

    int[] fileIds = new int[contentsToInsert];
    for (int i = 0; i < contentsToInsert; i++) {
      int fileId = vfs.createRecord();
      String content = "testContent_" + (i % uniqueContentsCount);
      try (DataOutputStream stream = vfs.writeContent(fileId, /*fixed: */false)) {
        stream.writeUTF(content);
      }
      fileIds[i] = fileId;
    }

    //read back:
    for (int i = 0; i < contentsToInsert; i++) {
      int fileId = fileIds[i];
      String expectedContent = "testContent_" + (i % uniqueContentsCount);
      try (DataInputStream stream = vfs.readContent(fileId)) {
        String actualContent = stream.readUTF();
        assertEquals(
          expectedContent,
          actualContent,
          "[i:" + i + "][fileId:" + fileId + "]: written content must be read back"
        );
      }
    }
  }

  @BeforeEach
  void setUp(@TempDir Path vfsDir) {
    vfs = FSRecordsImpl.connect(vfsDir, Collections.emptyList(), false, FSRecordsImpl.ON_ERROR_RETHROW);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (vfs != null) {
      StorageTestingUtils.bestEffortToCloseAndClean(vfs);
    }
  }
}