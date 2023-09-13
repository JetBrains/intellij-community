// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.intellij.openapi.util.io.FileAttributes;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

public class FSRecordsImpl_MultiThreaded_Test {


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
          vfs.updateRecordFields(fileId, 1, attributes, "file_"+i, true);
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

  @BeforeEach
  void setUp(@TempDir Path vfsDir) {
    vfs = FSRecordsImpl.connect(vfsDir);
  }

  @AfterEach
  void tearDown() {
    if (vfs != null) {
      vfs.dispose();
    }
  }
}