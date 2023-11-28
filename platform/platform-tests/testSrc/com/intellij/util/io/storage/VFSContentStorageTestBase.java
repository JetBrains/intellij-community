// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.storage;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.BlobStorageTestBase;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

public abstract class VFSContentStorageTestBase<T extends VFSContentStorage> {

  /** (32k records) * (~20Kb avg record size) ~= 700Mb -> must fit a typical heap without OoM */

  private static final int MANY_RECORDS = 32 * 1024;

  private T storage;
  private Path storagePath;

  @Test
  public void initiallyStorageIsEmptyAndContainsZeroRecords() throws IOException {
    assertTrue(
      storage.isEmpty(),
      "Initially storage is empty"
    );
    assertEquals(
      0,
      storage.getRecordsCount(),
      "Initially storage has 0 records"
    );
  }

  @Test
  public void assignedStorageVersion_couldBeReadBack() throws IOException {
    int versionToSet = 42;
    storage.setVersion(versionToSet);
    assertEquals(
      versionToSet,
      storage.getVersion(),
      "Set version mus be returned");
  }

  @Test
  public void assignedStorageVersion_couldBeReadBack_AfterReopen() throws IOException {
    int versionToSet = 42;
    storage.setVersion(versionToSet);

    reopenStorage();

    assertEquals(
      versionToSet,
      storage.getVersion(),
      "Set version mus be returned");
  }


  @Test
  public void singleRecordWritten_couldBeReadBackAsIs() throws IOException {
    String stringToWrite = "any random string";
    int id = storage.storeRecord(contentOfString(stringToWrite));

    assertEquals(1, storage.getRecordsCount(), "Must be 1 record in storage");
    assertFalse(storage.isEmpty(), "Storage is !empty after first record was stored");

    String stringReadBack = readAsString(storage.readStream(id));
    assertEquals(
      stringToWrite,
      stringReadBack,
      "Content stored and read back must be the same"
    );
  }

  @Test
  public void singleRecordWritten_couldBeReadBackAsIs_afterReopen() throws IOException {
    String stringToWrite = "any random string";
    int id = storage.storeRecord(contentOfString(stringToWrite));

    reopenStorage();

    assertEquals(1, storage.getRecordsCount(), "Must be 1 record in storage");
    assertFalse(storage.isEmpty(), "Storage is !empty after first record was stored");

    String stringReadBack = readAsString(storage.readStream(id));
    assertEquals(
      stringToWrite,
      stringReadBack,
      "Content stored and read back must be the same"
    );
  }

  @Test
  public void sameRecordStoredTwice_GetSameId() throws IOException {
    String stringToWrite = "any random string";
    int id1 = storage.storeRecord(contentOfString(stringToWrite));
    int id2 = storage.storeRecord(contentOfString(stringToWrite));

    assertEquals(
      id1,
      id2,
      "Same content stored 2nd time must be assigned same id");
    assertEquals(
      1,
      storage.getRecordsCount(),
      "Must be only 1 record in storage -- same content must NOT be stored twice"
    );
  }


  @Test
  public void manyRecordsWritten_couldBeReadBackAsIs() throws IOException {
    String[] stringsToWrite = generateContents(MANY_RECORDS);
    for (String stringToWrite : stringsToWrite) {
      ByteArraySequence bytes = contentOfString(stringToWrite);
      int id = storage.storeRecord(bytes);

      String stringReadBack = readAsString(storage.readStream(id));
      assertEquals(
        stringToWrite,
        stringReadBack,
        "Content stored and read back must be the same"
      );
    }

    assertFalse(
      storage.isEmpty(),
      "Storage is !empty after so many records were stored"
    );
    assertEquals(
      stringsToWrite.length,
      storage.getRecordsCount(),
      "Must be " + stringsToWrite.length + " record in storage"
    );
  }

  @Test
  public void manyRecordsWritten_couldBeReadBackAsIs_afterReopen() throws IOException {
    String[] stringsToWrite = generateContents(MANY_RECORDS);
    int[] recordIds = new int[stringsToWrite.length];
    for (int i = 0; i < stringsToWrite.length; i++) {
      String stringToWrite = stringsToWrite[i];
      int id = storage.storeRecord(contentOfString(stringToWrite));
      recordIds[i] = id;
    }

    reopenStorage();

    assertFalse(
      storage.isEmpty(),
      "Storage is !empty after so many records were stored"
    );

    assertEquals(
      stringsToWrite.length,
      storage.getRecordsCount(),
      "Must be " + stringsToWrite.length + " record in storage"
    );

    for (int i = 0; i < recordIds.length; i++) {
      int id = recordIds[i];
      String stringToWrite = stringsToWrite[i];
      String stringReadBack = readAsString(storage.readStream(id));
      assertEquals(
        stringToWrite,
        stringReadBack,
        "Content stored and read back must be the same"
      );
    }
  }

  @Test
  public void manyRecordsWritten_couldBeReadBackAsIs_afterReopen_viaIterator() throws IOException {
    String[] stringsToWrite = generateContents(MANY_RECORDS);
    int[] recordIds = new int[stringsToWrite.length];
    for (int i = 0; i < stringsToWrite.length; i++) {
      String stringToWrite = stringsToWrite[i];
      int id = storage.storeRecord(contentOfString(stringToWrite));
      recordIds[i] = id;
    }

    reopenStorage();


    RecordIdIterator iterator = storage.createRecordIdIterator();
    for (int i = 0; i < recordIds.length && iterator.hasNextId(); i++) {
      int id = iterator.nextId();
      int recordId = recordIds[i];
      assertEquals(
        id,
        recordId,
        "id[#" + i + "]: " + recordId + " but id from iterator " + id
      );
      String stringToWrite = stringsToWrite[i];
      String stringReadBack = readAsString(storage.readStream(id));
      assertEquals(
        stringToWrite,
        stringReadBack,
        "Content stored and read back must be the same"
      );
    }
  }


  @Test
  public void manyRecordsWrittenTwice_storedOnlyOnce() throws IOException {
    String[] stringsToWrite = generateContents(MANY_RECORDS);
    int[] recordIds = new int[stringsToWrite.length];
    for (int i = 0; i < stringsToWrite.length; i++) {
      String stringToWrite = stringsToWrite[i];
      int id = storage.storeRecord(contentOfString(stringToWrite));
      recordIds[i] = id;
    }
    assertEquals(
      stringsToWrite.length,
      storage.getRecordsCount(),
      "Must be " + stringsToWrite.length + " record in storage"
    );

    for (int i = 0; i < stringsToWrite.length; i++) {
      String stringToWrite = stringsToWrite[i];
      int id = storage.storeRecord(contentOfString(stringToWrite));
      assertEquals(
        recordIds[i],
        id,
        "Same content stored twice -- must be assigned the same id"
      );
    }
    assertEquals(
      stringsToWrite.length,
      storage.getRecordsCount(),
      "Must be still " + stringsToWrite.length + " record in storage -- no additional duplicates"
    );
  }

  @Test
  public void manyRecordsWrittenTwice_storedOnlyOnce_afterReopen() throws IOException {
    String[] stringsToWrite = generateContents(MANY_RECORDS);
    int[] recordIds = new int[stringsToWrite.length];
    for (int i = 0; i < stringsToWrite.length; i++) {
      String stringToWrite = stringsToWrite[i];
      int id = storage.storeRecord(contentOfString(stringToWrite));
      recordIds[i] = id;
    }
    assertEquals(
      stringsToWrite.length,
      storage.getRecordsCount(),
      "Must be " + stringsToWrite.length + " record in storage"
    );

    reopenStorage();

    for (int i = 0; i < stringsToWrite.length; i++) {
      String stringToWrite = stringsToWrite[i];
      int id = storage.storeRecord(contentOfString(stringToWrite));
      assertEquals(
        recordIds[i],
        id,
        "Same content stored twice -- must be assigned the same id"
      );
    }
    assertEquals(
      stringsToWrite.length,
      storage.getRecordsCount(),
      "Must be still " + stringsToWrite.length + " record in storage -- no additional duplicates"
    );
  }


  @Test
  public void manyRecordsWrittenTwice_storedOnlyOnce_multiThreaded() throws IOException, InterruptedException {
    String[] stringsToWrite = generateContents(MANY_RECORDS);
    int[] recordIds = new int[stringsToWrite.length];

    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Callable<Void>> tasks = IntStream.range(0, threads)
        .mapToObj(threadNo -> (Callable<Void>)() -> {
          for (int i = 0; i < stringsToWrite.length; i++) {
            String stringToWrite = stringsToWrite[i];
            int id = storage.storeRecord(contentOfString(stringToWrite));
            recordIds[i] = id;
          }
          return null;
        })
        .toList();
      pool.invokeAll(tasks);
    }
    finally {
      pool.shutdown();
    }

    assertEquals(
      stringsToWrite.length,
      storage.getRecordsCount(),
      "Must be " + stringsToWrite.length + " record in storage"
    );

    for (int i = 0; i < stringsToWrite.length; i++) {
      String stringToWrite = stringsToWrite[i];
      int id = storage.storeRecord(contentOfString(stringToWrite));
      assertEquals(
        recordIds[i],
        id,
        "Same content stored twice -- must be assigned the same id"
      );
    }
    assertEquals(
      stringsToWrite.length,
      storage.getRecordsCount(),
      "Must be still " + stringsToWrite.length + " record in storage -- no additional duplicates"
    );
  }


  //======================================== infrastructure: ==================================================//

  protected String[] generateContents(int count) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    IntSupplier sizeGenerator = () -> {
      int mediumContentSize = 1 << 10;
      int hugeContentSize = 1 << 20;
      //generate 98% of records in [0..mediumSize], but 2% up to the hugeSize
      //average record size = (1M*0.02+1K*0.98) ~ 20K
      if (rnd.nextInt(50) == 0) {
        return rnd.nextInt(hugeContentSize);
      }
      else {
        return rnd.nextInt(mediumContentSize);
      }
    };
    return IntStream.iterate(0, i -> i + 1)
      .mapToObj(i -> BlobStorageTestBase.randomString(rnd, sizeGenerator.getAsInt()))
      .distinct()
      .limit(count)
      .toArray(String[]::new);
  }

  protected abstract @NotNull T openStorage(@NotNull Path storagePath) throws IOException;

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws IOException {
    storagePath = tempDir.resolve("content-storage");
    storage = openStorage(storagePath);
  }

  @AfterEach
  void tearDown() throws IOException {
    if (storage != null) {
      //do storage self-check (full, not fast one):
      try {
        RecordIdIterator iterator = storage.createRecordIdIterator();
        while (iterator.hasNextId()) {
          int id = iterator.nextId();
          storage.checkRecord(id, /*fast: */ false);
        }
      }
      finally {
        storage.closeAndClean();
      }
    }
  }


  protected final void reopenStorage() throws IOException {
    storage.close();
    storage = openStorage(storagePath);
  }

  private static @NotNull ByteArraySequence contentOfString(@NotNull String content) {
    return ByteArraySequence.create(content.getBytes(UTF_8));
  }

  private static String readAsString(@NotNull InputStream input) throws IOException {
    return new String(input.readAllBytes(), UTF_8);
  }
}