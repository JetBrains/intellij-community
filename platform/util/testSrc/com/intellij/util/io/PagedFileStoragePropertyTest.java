// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.indexing.impl.storage.DefaultIndexStorageLayoutProviderKt;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PagedFileStoragePropertyTest {

  @TempDir
  Path tempDir;
  private Path path;

  /** Make the page size small enough for page-border issues to be tested too */
  private static final int PAGE_SIZE = 64 * 1024;

  private static final StorageLockContext REGULAR_CONTEXT = new StorageLockContext();
  private static final StorageLockContext INDEXES_CONTEXT = DefaultIndexStorageLayoutProviderKt.newStorageLockContext();

  private static List<Named<StorageLockContext>> parameters() {
    return List.of(
      Named.of("regular StorageLockContext", REGULAR_CONTEXT),
      Named.of("Indexes (+WAL?) StorageLockContext", INDEXES_CONTEXT)
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parameters")
  public void putInts_couldBeGetBack(StorageLockContext storageLockContext) throws IOException {
    int[] samples = ThreadLocalRandom.current().ints(1024).toArray();
    int sampleSize = Integer.BYTES;

    try (Storage storage = openStorage(storageLockContext)) {
      for (long offsetInFile = 0; offsetInFile < 512L * sampleSize * samples.length; offsetInFile += sampleSize) {
        int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
        int sample = samples[index];
        storage.storage.putInt(offsetInFile, sample);
      }

      long fileLength = storage.storage.length();
      for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += sampleSize) {
        int value = storage.storage.getInt(offsetInFile);
        int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
        int sample = samples[index];
        assertEquals(
          sample,
          value,
          "getInt(" + offsetInFile + ") != samples[" + index + "]"
        );
      }
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parameters")
  public void putInts_couldBeGetBack_afterReopen(StorageLockContext storageLockContext) throws IOException {
    int[] samples = ThreadLocalRandom.current().ints(1024).toArray();
    int sampleSize = Integer.BYTES;

    try (Storage storage = openStorage(storageLockContext)) {
      for (long offsetInFile = 0; offsetInFile < 512L * sampleSize * samples.length; offsetInFile += sampleSize) {
        int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
        int sample = samples[index];
        storage.storage.putInt(offsetInFile, sample);
      }

      storage.closeWithoutClean();
    }

    //Reopen and check the values written before are still readable
    try (Storage storage = openStorage(storageLockContext)) {
      long fileLength = storage.storage.length();
      for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += sampleSize) {
        int value = storage.storage.getInt(offsetInFile);
        int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
        int sample = samples[index];
        assertEquals(
          sample,
          value,
          "getInt(" + offsetInFile + ") != samples[" + index + "]"
        );
      }
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parameters")
  public void putLongs_couldBeGetBack(StorageLockContext storageLockContext) throws IOException {
    long[] samples = ThreadLocalRandom.current().longs(1024).toArray();
    int sampleSize = Long.BYTES;

    try (Storage storage = openStorage(storageLockContext)) {
      for (long offsetInFile = 0; offsetInFile < 512L * sampleSize * samples.length; offsetInFile += sampleSize) {
        int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
        long sample = samples[index];
        storage.storage.putLong(offsetInFile, sample);
      }

      long fileLength = storage.storage.length();
      for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += sampleSize) {
        long value = storage.storage.getLong(offsetInFile);
        int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
        long sample = samples[index];
        assertEquals(
          sample,
          value,
          "getInt(" + offsetInFile + ") != samples[" + index + "]"
        );
      }
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parameters")
  public void putLongs_couldBeGetBack_afterReopen(StorageLockContext storageLockContext) throws IOException {
    long[] samples = ThreadLocalRandom.current().longs(1024).toArray();
    int sampleSize = Long.BYTES;

    try (Storage storage = openStorage(storageLockContext)) {
      for (long offsetInFile = 0; offsetInFile < 512L * sampleSize * samples.length; offsetInFile += sampleSize) {
        int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
        long sample = samples[index];
        storage.storage.putLong(offsetInFile, sample);
      }

      storage.closeWithoutClean();
    }

    //Reopen and check the values written before are still readable
    try (Storage storage = openStorage(storageLockContext)) {
      long fileLength = storage.storage.length();
      for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += sampleSize) {
        long value = storage.storage.getLong(offsetInFile);
        int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
        long sample = samples[index];
        assertEquals(
          sample,
          value,
          "getInt(" + offsetInFile + ") != samples[" + index + "]"
        );
      }
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parameters")
  public void putByte_couldBeGetBack(StorageLockContext storageLockContext) throws IOException {
    byte[] samples = new byte[4096];
    ThreadLocalRandom.current().nextBytes(samples);
    int sampleSize = 1;

    try (Storage storage = openStorage(storageLockContext)) {
      for (long offsetInFile = 0; offsetInFile < 512L * sampleSize * samples.length; offsetInFile += sampleSize) {
        int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
        byte sample = samples[index];
        storage.storage.put(offsetInFile, sample);
      }

      long fileLength = storage.storage.length();
      for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += sampleSize) {
        byte value = storage.storage.get(offsetInFile, false);
        int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
        byte sample = samples[index];
        assertEquals(
          sample,
          value,
          "getInt(" + offsetInFile + ") != samples[" + index + "]"
        );
      }
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parameters")
  public void putByte_couldBeGetBack_afterReopen(StorageLockContext storageLockContext) throws IOException {
    byte[] samples = new byte[4096];
    ThreadLocalRandom.current().nextBytes(samples);
    int sampleSize = 1;

    try (Storage storage = openStorage(storageLockContext)) {
      for (long offsetInFile = 0; offsetInFile < 512L * sampleSize * samples.length; offsetInFile += sampleSize) {
        int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
        byte sample = samples[index];
        storage.storage.put(offsetInFile, sample);
      }

      storage.closeWithoutClean();
    }

    //Reopen and check the values written before are still readable
    try (Storage storage = openStorage(storageLockContext)) {
      long fileLength = storage.storage.length();
      for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += sampleSize) {
        byte value = storage.storage.get(offsetInFile, false);
        int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
        byte sample = samples[index];
        assertEquals(
          sample,
          value,
          "getInt(" + offsetInFile + ") != samples[" + index + "]"
        );
      }
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parameters")
  public void putBytes_couldBeGetBack(StorageLockContext storageLockContext) throws IOException {
    byte[] samples = new byte[4096];
    ThreadLocalRandom.current().nextBytes(samples);

    try (Storage storage = openStorage(storageLockContext)) {
      for (long offsetInFile = 0; offsetInFile < 512 * samples.length; offsetInFile += samples.length) {
        storage.storage.put(offsetInFile, samples, 0, samples.length);
      }

      long fileLength = storage.storage.length();
      byte[] buffer = new byte[samples.length];
      for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += samples.length) {
        storage.storage.get(offsetInFile, buffer, 0, buffer.length, false);
        assertArrayEquals(
          samples,
          buffer,
          "getInt(" + offsetInFile + ")"
        );
      }
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parameters")
  public void putBytes_couldBeGetBack_afterReopen(StorageLockContext storageLockContext) throws IOException {
    byte[] samples = new byte[4096];
    ThreadLocalRandom.current().nextBytes(samples);

    try (Storage storage = openStorage(storageLockContext)) {
      for (long offsetInFile = 0; offsetInFile < 512 * samples.length; offsetInFile += samples.length) {
        storage.storage.put(offsetInFile, samples, 0, samples.length);
      }

      storage.closeWithoutClean();
    }

    //Reopen and check the values written before are still readable
    try (Storage storage = openStorage(storageLockContext)) {
      long fileLength = storage.storage.length();
      byte[] buffer = new byte[samples.length];
      for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += samples.length) {
        storage.storage.get(offsetInFile, buffer, 0, buffer.length, false);
        assertArrayEquals(
          samples,
          buffer,
          "getInt(" + offsetInFile + ")"
        );
      }
    }
  }

  //===================== infrastructure: ====================================================================================//

  @BeforeEach
  void createStorageFile() throws IOException {
    path = Files.createFile(tempDir.resolve("storage"));
  }

  private Storage openStorage(@NotNull StorageLockContext storageLockContext) {
    return new Storage(path, storageLockContext);
  }

  /** Guards [StorageLockContext] locking, and cleans the file after itself */
  private static class Storage implements AutoCloseable {
    final PagedFileStorage storage;
    private final @NotNull StorageLockContext storageLockContext;
    private boolean cleanOnClose = true;

    private Storage(@NotNull Path path, @NotNull StorageLockContext storageLockContext) {
      this.storageLockContext = storageLockContext;
      storageLockContext.lockWrite();
      try {
        this.storage = new PagedFileStorage(path, storageLockContext, PAGE_SIZE, /*valuesAligned:*/ false, /*nativeByteOrder: */ false);
      }
      catch (Throwable t) {
        storageLockContext.unlockWrite();
        throw t;
      }
    }

    /** Marks the storage to be closed without the file deletion, so the file could be opened again later */
    public void closeWithoutClean() {
      cleanOnClose = false;
    }

    @Override
    public void close() throws IOException {
      try {
        if (cleanOnClose) {
          storage.closeAndClean();
        }
        else {
          storage.close();
        }
      }
      finally {
        storageLockContext.unlockWrite();
      }
    }
  }
}
