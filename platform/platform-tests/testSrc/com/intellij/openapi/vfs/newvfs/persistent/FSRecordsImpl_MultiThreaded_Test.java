// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.platform.util.io.storages.StorageTestingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
  public void concurrentlyInsertedRoots_AreStillConsistent() throws Exception {
    int CPUs = Runtime.getRuntime().availableProcessors();
    int totalRoots = 100_000;//too many roots could exceed attribute storage max record size
    int rootsPerThread = totalRoots / CPUs;
    ExecutorService pool = Executors.newFixedThreadPool(CPUs);
    try {
      List<Future<List<String>>> futures  = IntStream.range(0, CPUs)
        .mapToObj(threadNo -> (Callable<List<String>>)() -> {
          List<String> rootsUrls = new ArrayList<>(rootsPerThread);
          for (int i = 0; i < rootsPerThread; i++) {
            String rootUrl = "file:///root/" + threadNo + "/" + i;
            rootsUrls.add(rootUrl);
          }
          for (String rootUrl : rootsUrls) {
            vfs.findOrCreateRootRecord(rootUrl);
          }
          return rootsUrls;
        })
        .map(pool::submit)
        .toList();

      List<String> allRootUrls = new ArrayList<>(totalRoots);
      for (Future<List<String>> future : futures) {
        allRootUrls.addAll(future.get());//give a chance to deliver exception
      }

      assertEquals(totalRoots,
                   vfs.listRoots().length,
                   "Must be " + totalRoots + " roots");

      List<String> allRootUrlsFromVFS = new ArrayList<>(totalRoots);
      vfs.forEachRoot((rootUrl, rootId) -> {
        allRootUrlsFromVFS.add(rootUrl);
      });

      Collections.sort(allRootUrls);
      Collections.sort(allRootUrlsFromVFS);
      assertEquals(
        allRootUrlsFromVFS,
        allRootUrls,
        "Only roots inserted must be returned by .forEachRoot()"
      );

    }
    finally {
      pool.shutdown();
      pool.awaitTermination(15, SECONDS);
    }
  }

  @BeforeEach
  void setUp(@TempDir Path vfsDir) {
    vfs = FSRecordsImpl.connect(vfsDir, FSRecordsImpl.ON_ERROR_RETHROW);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (vfs != null) {
      StorageTestingUtils.bestEffortToCloseAndClean(vfs);
    }
  }

}