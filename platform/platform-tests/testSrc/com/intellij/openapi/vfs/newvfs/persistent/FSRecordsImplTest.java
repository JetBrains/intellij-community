// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.platform.util.io.storages.StorageTestingUtils;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
      try (DataInputStream stream = new DataInputStream(vfs.readContent(fileId))){
        String actualContent = stream.readUTF();
        assertEquals(
          expectedContent,
          actualContent,
          "[i:" + i + "][fileId:" + fileId + "]: written content must be read back"
        );
      }
    }
  }

  @Test
  public void fileRecordModCountChanges_onlyIfFileContentActuallyChanges() throws IOException {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    String[] randomContents = Stream.generate(
        () -> StorageTestingUtils.randomString(rnd, rnd.nextInt(0, 1024))
      )
      .limit(1024)
      .toArray(String[]::new);

    int fileId = vfs.createRecord();

    String previousContent = "";
    writeContent(fileId, previousContent);
    for (int attempt = 0; attempt < 1024; attempt++) {
      int modCountBefore = vfs.getModCount(fileId);
      String content = randomContents[rnd.nextInt(randomContents.length)];
      writeContent(fileId, content);
      int modCountAfter = vfs.getModCount(fileId);

      if (previousContent.equals(content)) {
        assertEquals(
          modCountAfter, modCountBefore,
          "[" + previousContent + "]->[" + content + "]:" +
          " modCountAfter(" + modCountAfter + ") most be == modCountBefore(" + modCountBefore + ") since content doesn't change"
        );
      }
      else {
        //'+1' is a bit of over-specification: modCount 'after' must be more than 'before' -- but it doesn't really
        // matter how much more. But I also don't want to grow modCount without a reason, so I check the minimum
        // increment:
        assertEquals(
          modCountAfter, modCountBefore + 1,
          "[" + previousContent + "]->[" + content + "]:" +
          "modCountAfter(" + modCountAfter + ") most be modCountBefore(" + modCountBefore + ")+1 since content does change"
        );
      }
      previousContent = content;
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

  private void writeContent(int fileId,
                            @NotNull String content) {
    vfs.writeContent(fileId, new ByteArraySequence(content.getBytes(UTF_8)), true);
  }
}