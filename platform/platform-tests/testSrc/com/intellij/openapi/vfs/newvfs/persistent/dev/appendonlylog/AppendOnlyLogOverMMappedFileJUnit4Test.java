// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog;

import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.MMappedFileStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.BlobStorageTestBase;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static com.intellij.util.io.IOUtil.readString;
import static java.nio.charset.StandardCharsets.UTF_8;
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
  public static final int MAX_RECORD_SIZE = 1024;

  /** Make page smaller to increase the chance of page-border issues to manifest */
  private static final int PAGE_SIZE = 1 << 18;

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
    long recordId = appendOnlyLog.append(dataToWrite.getBytes(UTF_8));
    String dataReadBack = appendOnlyLog.read(recordId, IOUtil::readString);
    assertEquals("Data written must be the data read back",
                 dataToWrite,
                 dataReadBack);
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
    assertEquals("Data written must be the data read back",
                 dataToWrite,
                 dataReadBackRef.get());
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
      assertEquals("[" + i + "]: data written must be the data read back",
                   stringsToWrite[i],
                   stringReadBack);
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
      assertEquals("[" + i + "]: data written must be the data read back",
                   stringsToWrite[i.get()],
                   stringReadBack);
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
      String stringReadBack = appendOnlyLog.read(recordId, buffer -> readString(buffer));
      assertEquals("[" + i + "]: data written must be the data read back",
                   stringsToWrite[i],
                   stringReadBack);
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