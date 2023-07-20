// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.MMappedFileStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.BlobStorageTestBase;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;

/**
 * FIXME: this junit4 version is temporary copy of {@link AppendOnlyLogOverMMappedFileJUnit5Test}. Both
 * are functionally identical, both junit4 and junit5 versions are succeeded locally -- but junit5
 * version currently fails CI, while junit4 (this) version is OK. After this issue will be investigated
 * and fixed -- remove this test, and leave only junit5 version
 */
public class AppendOnlyLogOverMMappedFileJUnit4Test {
  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final int ENOUGH_RECORDS = 1 << 20;
  /** Make page smaller to increase the chance of page-border issues to manifest */
  private static final int PAGE_SIZE = 1 << 15;

  private AppendOnlyLogOverMMappedFile appendOnlyLog;

  @Before
  public void setUp() throws IOException {
    Path tempDir = temporaryFolder.newFolder().toPath();
    Path storageFile = tempDir.resolve("appendOnlyLog");
    appendOnlyLog = openLog(storageFile);
  }

  @After
  public void tearDown() throws IOException {
    if (appendOnlyLog != null) {
      appendOnlyLog.closeAndRemove();
    }
  }

  @Test
  public void singleRecordWritten_CouldBeReadBackAsIs() throws Exception {
    String dataToWrite = "test data";
    long recordId = appendOnlyLog.writeRecord(dataToWrite.getBytes(US_ASCII));
    String dataReadBack = appendOnlyLog.readRecord(recordId, buffer -> readString(buffer));
    assertEquals("Data written must be the data read back",
                 dataToWrite,
                 dataReadBack);
  }

  @Test
  public void manyRecordsWritten_CouldBeReadBackAsIs() throws Exception {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    String[] stringsToWrite = Stream.generate(() -> {
        return BlobStorageTestBase.randomString(rnd, rnd.nextInt(1, 1024));
      })
      .limit(ENOUGH_RECORDS)
      .toArray(String[]::new);

    long[] recordsIds = new long[stringsToWrite.length];
    for (int i = 0; i < stringsToWrite.length; i++) {
      final String toWrite = stringsToWrite[i];
      long recordId = appendOnlyLog.writeRecord(toWrite.getBytes(US_ASCII));
      recordsIds[i] = recordId;
    }

    for (int i = 0; i < recordsIds.length; i++) {
      long recordId = recordsIds[i];
      String stringReadBack = appendOnlyLog.readRecord(recordId, buffer -> readString(buffer));
      assertEquals("[" + i + "]: data written must be the data read back",
                   stringsToWrite[i],
                   stringReadBack);
    }
  }

  @Test
  public void manyRecordsWritten_CouldBeReadBackAsIs_AfterReopen() throws Exception {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    String[] stringsToWrite = Stream.generate(() -> {
        return BlobStorageTestBase.randomString(rnd, rnd.nextInt(1, 1024));
      })
      .limit(ENOUGH_RECORDS)
      .toArray(String[]::new);

    long[] recordsIds = new long[stringsToWrite.length];
    for (int i = 0; i < stringsToWrite.length; i++) {
      final String toWrite = stringsToWrite[i];
      long recordId = appendOnlyLog.writeRecord(toWrite.getBytes(US_ASCII));
      recordsIds[i] = recordId;
    }

    appendOnlyLog.close();
    appendOnlyLog = openLog(appendOnlyLog.storagePath());

    for (int i = 0; i < recordsIds.length; i++) {
      long recordId = recordsIds[i];
      String stringReadBack = appendOnlyLog.readRecord(recordId, buffer -> readString(buffer));
      assertEquals("[" + i + "]: data written must be the data read back",
                   stringsToWrite[i],
                   stringReadBack);
    }
  }


  //====================== infrastructure ===========================================================================
  private @NotNull AppendOnlyLogOverMMappedFile openLog(@NotNull Path storageFile) throws IOException {
    MMappedFileStorage mappedStorage = new MMappedFileStorage(
      storageFile,
      PAGE_SIZE
    );
    return new AppendOnlyLogOverMMappedFile(mappedStorage);
  }

  private static String readString(@NotNull ByteBuffer buffer) {
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return new String(bytes, US_ASCII);
  }
}