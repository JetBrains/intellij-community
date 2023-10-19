// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog;

import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.BlobStorageTestBase;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static com.intellij.util.io.IOUtil.readString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class AppendOnlyLogOverMMappedFileTest {

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final int ENOUGH_RECORDS = 1 << 20;
  public static final int MAX_RECORD_SIZE = 1024;

  /** Make page smaller to increase the chance of page-border issues to manifest */
  private static final int PAGE_SIZE = 1 << 18;

  /** we could open >1 log -- just keep all them in the list, to closeAndClean() afterwards */
  private static final List<AppendOnlyLogOverMMappedFile> openedLogs = new ArrayList<>();

  private AppendOnlyLogOverMMappedFile appendOnlyLog;


  @Before
  public void setUp() throws IOException {
    Path tempDir = temporaryFolder.newFolder().toPath();
    Path storageFile = tempDir.resolve("appendOnlyLog");
    appendOnlyLog = openLog(storageFile);
  }

  @After
  public void tearDown() throws IOException {
    for (AppendOnlyLogOverMMappedFile log : openedLogs) {
      log.closeAndUnsafelyUnmap();
    }
    for (AppendOnlyLogOverMMappedFile log : openedLogs) {
      log.closeAndClean();
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

    long[] recordsIds = writeRecords(stringsToWrite);

    checkRecordsReadBack(recordsIds, stringsToWrite);
  }

  @Test
  public void manyRecordsWritten_CouldBeReadBackAsIs_viaForEach() throws Exception {
    String[] stringsToWrite = generateRandomStrings(ENOUGH_RECORDS);

    long[] recordIds = writeRecords(stringsToWrite);

    checkRecordsReadBack_ViaForEach(recordIds, stringsToWrite);
  }

  @Test
  public void manyRecordsWritten_AfterReopen_CouldBeReadBackAsIs() throws Exception {
    String[] stringsToWrite = generateRandomStrings(ENOUGH_RECORDS);

    long[] recordsIds = writeRecords(stringsToWrite);

    appendOnlyLog.close();
    appendOnlyLog = openLog(appendOnlyLog.storagePath());

    checkRecordsReadBack(recordsIds, stringsToWrite);
  }

  @Test
  public void manyRecordsWritten_AfterReopen_CouldBeReadBackAsIs_viaForEach() throws Exception {
    String[] stringsToWrite = generateRandomStrings(ENOUGH_RECORDS);

    long[] recordIds = writeRecords(stringsToWrite);

    appendOnlyLog.close();
    appendOnlyLog = openLog(appendOnlyLog.storagePath());

    checkRecordsReadBack_ViaForEach(recordIds, stringsToWrite);
  }


  @Test
  public void hugeRecordsWritten_CouldBeReadBackAsIs() throws Exception {
    //Test page-border issues: generate strings of size ~= pageSize
    int headerSize = Integer.BYTES;
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    String[] stringsToWrite = Stream.generate(
        () -> BlobStorageTestBase.randomString(rnd, PAGE_SIZE + rnd.nextInt(-16, -headerSize))
      )
      .limit(128)
      .toArray(String[]::new);

    long[] recordsIds = writeRecords(stringsToWrite);

    checkRecordsReadBack(recordsIds, stringsToWrite);
  }

  @Test
  public void hugeRecordsWritten_AfterReopen_CouldBeReadBackAsIs() throws Exception {
    //Test page-border issues: generate strings of size ~= pageSize
    int headerSize = Integer.BYTES;
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    String[] stringsToWrite = Stream.generate(
        () -> BlobStorageTestBase.randomString(rnd, PAGE_SIZE + rnd.nextInt(-16, -headerSize))
      )
      .limit(128)
      .toArray(String[]::new);

    long[] recordsIds = writeRecords(stringsToWrite);

    appendOnlyLog.close();
    appendOnlyLog = openLog(appendOnlyLog.storagePath());

    checkRecordsReadBack(recordsIds, stringsToWrite);
  }

  @Test
  public void hugeRecordsWritten_CouldBeReadBackAsIs_viaForEach() throws Exception {
    //Test page-border issues: generate strings of size ~= pageSize
    int headerSize = Integer.BYTES;
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    String[] stringsToWrite = Stream.generate(
        () -> BlobStorageTestBase.randomString(rnd, PAGE_SIZE + rnd.nextInt(-16, -headerSize))
      )
      .limit(128)
      .toArray(String[]::new);

    long[] recordIds = writeRecords(stringsToWrite);

    checkRecordsReadBack_ViaForEach(recordIds, stringsToWrite);
  }

  @Test
  public void hugeRecordsWritten_AfterReopen_CouldBeReadBackAsIs_viaForEach() throws Exception {
    //Test page-border issues: generate strings of size ~= pageSize
    int headerSize = Integer.BYTES;
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    String[] stringsToWrite = Stream.generate(
        () -> BlobStorageTestBase.randomString(rnd, PAGE_SIZE + rnd.nextInt(-16, -headerSize))
      )
      .limit(128)
      .toArray(String[]::new);

    long[] recordIds = writeRecords(stringsToWrite);

    appendOnlyLog.close();
    appendOnlyLog = openLog(appendOnlyLog.storagePath());

    checkRecordsReadBack_ViaForEach(recordIds, stringsToWrite);
  }


  @Test
  public void logFailsToAppendPayload_LargerOrEqualsToPageSize() throws IOException {
    int headerSize = Integer.BYTES;

    //Must be OK: max payload size to fit a page
    appendOnlyLog.append(data -> data, PAGE_SIZE - headerSize);

    assertThrows(
      "Log must throws exception if full record (payload+header) doesn't fit into a page, ",
      IllegalArgumentException.class,
      () -> appendOnlyLog.append(data -> data, PAGE_SIZE - headerSize + 1)
    );
  }

  @Test
  public void closeIsSafeToCallTwice() throws IOException {
    appendOnlyLog.close();
    appendOnlyLog.close();
  }

  @Test
  public void closeAndClean_RemovesTheStorageFile() throws IOException {
    //RC: it is over-specification -- .closeAndClean() doesn't require to remove the file, only to clean the
    //    content so new storage opened on top of it will be as-new. But this is the current implementation
    //    of that spec:
    appendOnlyLog.closeAndClean();
    assertFalse(
      Files.exists(appendOnlyLog.storagePath()),
      "Storage file [" + appendOnlyLog.storagePath() + "] must not exist after .closeAndClean()"
    );
  }

  //Special/edge cases, regressions:

  @Test
  public void singleEmptyRecordWritten_CouldBeReadBackAsIs() throws Exception {
    byte[] dataToWrite = new byte[0];
    long recordId = appendOnlyLog.append(dataToWrite);
    byte[] dataReadBack = appendOnlyLog.read(recordId, buffer -> {
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      return bytes;
    });
    assertArrayEquals("Data written must be the data read back",
                      dataToWrite,
                      dataReadBack);
  }

  @Test
  public void singleEmptyRecordWritten_CouldBeReadBackAsIs_viaForEach() throws Exception {
    byte[] dataToWrite = {};
    appendOnlyLog.append(dataToWrite);
    Ref<byte[]> dataReadBackRef = new Ref<>();
    appendOnlyLog.forEachRecord((id, buffer) -> {
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      dataReadBackRef.set(bytes);
      return true;
    });
    assertArrayEquals("Data written must be the data read back",
                      dataToWrite,
                      dataReadBackRef.get());
  }

  @Test
  public void ifEmptyRecordIsLastOnPage_itStillCouldBeReadBack() throws IOException {
    //Regression: (if recordPayload=0 && record is last on the page) => such record was skipped by .forEach()
    //            ('cos mistreated as 32b-alignment record):

    int roomOnFirstPage = PAGE_SIZE - AppendOnlyLogOverMMappedFile.HeaderLayout.HEADER_SIZE;

    byte[] recordAlmostFillUpFirstPage = new byte[roomOnFirstPage - 4 - 4];
    byte[] emptyRecord = new byte[0];
    appendOnlyLog.append(recordAlmostFillUpFirstPage);

    long emptyRecordId = appendOnlyLog.append(emptyRecord);
    byte[] emptyRecordReadBack = appendOnlyLog.read(emptyRecordId, AppendOnlyLogOverMMappedFileTest::readBytes);
    assertArrayEquals(
      "Empty record must be successfully read back by id",
      emptyRecord,
      emptyRecordReadBack
    );


    ArrayList<byte[]> records = new ArrayList<>();
    appendOnlyLog.forEachRecord((recordId, buffer) -> {
      records.add(readBytes(buffer));
      return true;
    });
    assertEquals(
      "2 records must be read by .forEach()",
      2,
      records.size()
    );
    assertArrayEquals(
      "Filling record must be successfully read back via .forEach()",
      recordAlmostFillUpFirstPage,
      records.get(0)
    );
    assertArrayEquals(
      "Empty record must be successfully read back via .forEach()",
      emptyRecord,
      records.get(1)
    );
  }

  //TODO test for recordId=NULL_ID processing (exception?)
  //TODO test for recordId=(padding record) processing (could happen after recovery)

  //====================== infrastructure: ===========================================================================

  private void checkRecordsReadBack_ViaForEach(long[] recordIds,
                                               String[] stringsWritten) throws IOException {
    IntRef i = new IntRef(0);
    appendOnlyLog.forEachRecord((recordId, buffer) -> {
      String stringReadBack = readString(buffer);
      String stringWritten = stringsWritten[i.get()];
      long expectedRecordId = recordIds[i.get()];
      if(!stringReadBack.equals(stringWritten)) {
        assertEquals("[" + i + "]: data written[recordId: " + expectedRecordId + "] must be the data read back[recordId: " + recordId + "]",
                     stringWritten,
                     stringReadBack);
      }

      assertEquals("[" + i + "]: recordId must be the same for data written back",
                   expectedRecordId,
                   recordId);
      i.inc();
      return true;
    });
  }

  private void checkRecordsReadBack(long[] recordsIds,
                                    String[] stringsWritten) throws IOException {
    for (int i = 0; i < recordsIds.length; i++) {
      long recordId = recordsIds[i];
      String stringReadBack = appendOnlyLog.read(recordId, IOUtil::readString);
      assertEquals("[" + i + "]: data written must be the data read back",
                   stringsWritten[i],
                   stringReadBack);
    }
  }

  private long[] writeRecords(String[] stringsToWrite) throws IOException {
    long[] recordsIds = new long[stringsToWrite.length];
    for (int i = 0; i < stringsToWrite.length; i++) {
      final String toWrite = stringsToWrite[i];
      long recordId = appendOnlyLog.append(toWrite.getBytes(UTF_8));
      recordsIds[i] = recordId;
    }
    return recordsIds;
  }

  private static @NotNull AppendOnlyLogOverMMappedFile openLog(@NotNull Path storageFile) throws IOException {
    AppendOnlyLogOverMMappedFile appendOnlyLog = AppendOnlyLogFactory
      .withDefaults()
      .pageSize(PAGE_SIZE)
      .ignoreDataFormatVersion()
      .open(storageFile);

    openedLogs.add(appendOnlyLog);

    return appendOnlyLog;
  }

  private static String[] generateRandomStrings(int stringsCount) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return Stream.generate(() -> {
        return BlobStorageTestBase.randomString(rnd, rnd.nextInt(0, MAX_RECORD_SIZE));
      })
      .limit(stringsCount)
      .toArray(String[]::new);
  }

  private static byte[] readBytes(@NotNull ByteBuffer buffer) {
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return bytes;
  }
}