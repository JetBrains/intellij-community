// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog;

import static com.intellij.util.io.IOUtil.readString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.MMappedFileStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.BlobStorageTestBase;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * FIXME: {@link AppendOnlyLogOverMMappedFileJUnit4Test} is functionally identical, but uses junit4
 * api. Both junit4 and junit5 versions are succeeded locally -- but junit5 version fails CI, while junit4
 * version is OK. Hence I disable junit5 until this investigated and fixed.
 */
@Disabled("There are seems to be problems with junit5 tests")
public class AppendOnlyLogOverMMappedFileJunit5Test {

  private static final int ENOUGH_RECORDS = 1 << 20;
  public static final int MAX_RECORD_SIZE = 1024;
  /** Make page smaller to increase the chance of page-border issues to manifest */
  private static final int PAGE_SIZE = 1 << 18;

  private AppendOnlyLogOverMMappedFile appendOnlyLog;

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws IOException {
    Path storageFile = tempDir.resolve("appendOnlyLog");
    appendOnlyLog = openLog(storageFile);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (appendOnlyLog != null) {
      appendOnlyLog.closeAndRemove();
    }
  }

  @Test
  public void singleRecordWritten_CouldBeReadBackAsIs() throws Exception {
    String dataToWrite = "test data";
    long recordId = appendOnlyLog.append(dataToWrite.getBytes(UTF_8));
    String dataReadBack = appendOnlyLog.read(recordId, buffer -> readString(buffer));
    assertEquals(dataToWrite,
                 dataReadBack,
                 "Data written must be the data read back");
  }

  @Test
  public void singleRecordWritten_CouldBeReadBackAsIs_viaForEach() throws Exception {
    String dataToWrite = "test data";
    appendOnlyLog.append(dataToWrite.getBytes(UTF_8));
    Ref<String> dataReadBackRef = new Ref<>();
    appendOnlyLog.forEachRecord((id, buffer) -> {
      dataReadBackRef.set(readString(buffer));
      return true;
    });
    assertEquals(dataToWrite,
                 dataReadBackRef.get(),
                 "Data written must be the data read back");
  }

  @Test
  public void manyRecordsWritten_CouldBeReadBackAsIs() throws Exception {
    String[] stringsToWrite = generateRandomStrings(ENOUGH_RECORDS);

    long[] recordsIds = new long[stringsToWrite.length];
    for (int i = 0; i < stringsToWrite.length; i++) {
      final String toWrite = stringsToWrite[i];
      long recordId = appendOnlyLog.append(toWrite.getBytes(UTF_8));
      recordsIds[i] = recordId;
    }

    for (int i = 0; i < recordsIds.length; i++) {
      long recordId = recordsIds[i];
      String stringReadBack = appendOnlyLog.read(recordId, IOUtil::readString);
      assertEquals(stringsToWrite[i],
                   stringReadBack,
                   "[" + i + "]: data written must be the data read back");
    }
  }

  @Test
  public void manyRecordsWritten_CouldBeReadBackAsIs_viaForEach() throws Exception {
    String[] stringsToWrite = generateRandomStrings(ENOUGH_RECORDS);

    for (final String toWrite : stringsToWrite) {
      appendOnlyLog.append(toWrite.getBytes(UTF_8));
    }

    IntRef i = new IntRef(0);
    appendOnlyLog.forEachRecord((recordId, buffer) -> {
      String stringReadBack = readString(buffer);
      assertEquals(stringsToWrite[i.get()],
                   stringReadBack,
                   "[" + i + "]: data written must be the data read back");
      i.inc();
      return true;
    });
  }

  @Test
  public void manyRecordsWritten_CouldBeReadBackAsIs_AfterReopen() throws Exception {
    String[] stringsToWrite = generateRandomStrings(ENOUGH_RECORDS);

    long[] recordsIds = new long[stringsToWrite.length];
    for (int i = 0; i < stringsToWrite.length; i++) {
      final String toWrite = stringsToWrite[i];
      long recordId = appendOnlyLog.append(toWrite.getBytes(UTF_8));
      recordsIds[i] = recordId;
    }

    appendOnlyLog.close();
    appendOnlyLog = openLog(appendOnlyLog.storagePath());

    for (int i = 0; i < recordsIds.length; i++) {
      long recordId = recordsIds[i];
      String stringReadBack = appendOnlyLog.read(recordId, IOUtil::readString);
      assertEquals(stringsToWrite[i],
                   stringReadBack,
                   "[" + i + "]: data written must be the data read back");
    }
  }


  //====================== infrastructure ===========================================================================

  private static @NotNull AppendOnlyLogOverMMappedFile openLog(@NotNull Path storageFile) throws IOException {
    MMappedFileStorage mappedStorage = new MMappedFileStorage(
      storageFile,
      PAGE_SIZE
    );
    return new AppendOnlyLogOverMMappedFile(mappedStorage);
  }

  private static String[] generateRandomStrings(int stringsCount) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return Stream.generate(() -> {
        return BlobStorageTestBase.randomString(rnd, rnd.nextInt(0, MAX_RECORD_SIZE));
      })
      .limit(stringsCount)
      .toArray(String[]::new);
  }
}