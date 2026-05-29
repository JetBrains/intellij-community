// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.impl.storage.DefaultIndexStorageLayoutProviderKt;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class PagedFileStoragePropertyTest {

  @Rule public TempDirectory tempDir = new TempDirectory();

  private final StorageLockContext storageLockContext;

  @SuppressWarnings("unused")
  public PagedFileStoragePropertyTest(@NotNull String name,
                                      @NotNull StorageLockContext context) { storageLockContext = context; }

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> parameters() {
    return List.of(
      new Object[]{"regular StorageLockContext", new StorageLockContext()},
      new Object[]{"Indexes (+WAL?) StorageLockContext", DefaultIndexStorageLayoutProviderKt.newStorageLockContext()}
    );
  }


  /** Make the page size small enough for page-border issues to be tested too */
  private static final int PAGE_SIZE = 64 * 1024;

  private Path path;
  private PagedFileStorage pagedFileStorage;


  @Test
  public void putInts_couldBeGetBack() throws IOException {
    int[] samples = ThreadLocalRandom.current().ints(1024).toArray();
    int sampleSize = Integer.BYTES;

    for (long offsetInFile = 0; offsetInFile < 512L * sampleSize * samples.length; offsetInFile += sampleSize) {
      int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
      int sample = samples[index];
      pagedFileStorage.putInt(offsetInFile, sample);
    }

    long fileLength = pagedFileStorage.length();
    for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += sampleSize) {
      int value = pagedFileStorage.getInt(offsetInFile);
      int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
      int sample = samples[index];
      assertEquals(
        "getInt(" + offsetInFile + ") != samples[" + index + "]",
        sample,
        value
      );
    }
  }

  @Test
  public void putInts_couldBeGetBack_afterReopen() throws IOException {
    int[] samples = ThreadLocalRandom.current().ints(1024).toArray();
    int sampleSize = Integer.BYTES;

    for (long offsetInFile = 0; offsetInFile < 512L * sampleSize * samples.length; offsetInFile += sampleSize) {
      int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
      int sample = samples[index];
      pagedFileStorage.putInt(offsetInFile, sample);
    }

    pagedFileStorage.close();
    pagedFileStorage = open(path);

    long fileLength = pagedFileStorage.length();
    for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += sampleSize) {
      int value = pagedFileStorage.getInt(offsetInFile);
      int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
      int sample = samples[index];
      assertEquals(
        "getInt(" + offsetInFile + ") != samples[" + index + "]",
        sample,
        value
      );
    }
  }


  @Test
  public void putLongs_couldBeGetBack() throws IOException {
    long[] samples = ThreadLocalRandom.current().longs(1024).toArray();
    int sampleSize = Long.BYTES;

    for (long offsetInFile = 0; offsetInFile < 512L * sampleSize * samples.length; offsetInFile += sampleSize) {
      int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
      long sample = samples[index];
      pagedFileStorage.putLong(offsetInFile, sample);
    }

    long fileLength = pagedFileStorage.length();
    for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += sampleSize) {
      long value = pagedFileStorage.getLong(offsetInFile);
      int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
      long sample = samples[index];
      assertEquals(
        "getInt(" + offsetInFile + ") != samples[" + index + "]",
        sample,
        value
      );
    }
  }

  @Test
  public void putLongs_couldBeGetBack_afterReopen() throws IOException {
    long[] samples = ThreadLocalRandom.current().longs(1024).toArray();
    int sampleSize = Long.BYTES;

    for (long offsetInFile = 0; offsetInFile < 512L * sampleSize * samples.length; offsetInFile += sampleSize) {
      int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
      long sample = samples[index];
      pagedFileStorage.putLong(offsetInFile, sample);
    }

    pagedFileStorage.close();
    pagedFileStorage = open(path);

    long fileLength = pagedFileStorage.length();
    for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += sampleSize) {
      long value = pagedFileStorage.getLong(offsetInFile);
      int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
      long sample = samples[index];
      assertEquals(
        "getInt(" + offsetInFile + ") != samples[" + index + "]",
        sample,
        value
      );
    }
  }


  @Test
  public void putByte_couldBeGetBack() throws IOException {
    byte[] samples = new byte[4096];
    ThreadLocalRandom.current().nextBytes(samples);
    int sampleSize = 1;

    for (long offsetInFile = 0; offsetInFile < 512L * sampleSize * samples.length; offsetInFile += sampleSize) {
      int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
      byte sample = samples[index];
      pagedFileStorage.put(offsetInFile, sample);
    }

    long fileLength = pagedFileStorage.length();
    for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += sampleSize) {
      byte value = pagedFileStorage.get(offsetInFile, false);
      int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
      byte sample = samples[index];
      assertEquals(
        "getInt(" + offsetInFile + ") != samples[" + index + "]",
        sample,
        value
      );
    }
  }

  @Test
  public void putByte_couldBeGetBack_afterReopen() throws IOException {
    byte[] samples = new byte[4096];
    ThreadLocalRandom.current().nextBytes(samples);
    int sampleSize = 1;

    for (long offsetInFile = 0; offsetInFile < 512L * sampleSize * samples.length; offsetInFile += sampleSize) {
      int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
      byte sample = samples[index];
      pagedFileStorage.put(offsetInFile, sample);
    }

    pagedFileStorage.close();
    pagedFileStorage = open(path);

    long fileLength = pagedFileStorage.length();
    for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += sampleSize) {
      byte value = pagedFileStorage.get(offsetInFile, false);
      int index = Math.toIntExact((offsetInFile / sampleSize) % samples.length);
      byte sample = samples[index];
      assertEquals(
        "getInt(" + offsetInFile + ") != samples[" + index + "]",
        sample,
        value
      );
    }
  }

  @Test
  public void putBytes_couldBeGetBack() throws IOException {
    byte[] samples = new byte[4096];
    ThreadLocalRandom.current().nextBytes(samples);

    for (long offsetInFile = 0; offsetInFile < 512 * samples.length; offsetInFile += samples.length) {
      pagedFileStorage.put(offsetInFile, samples, 0, samples.length);
    }

    long fileLength = pagedFileStorage.length();
    byte[] buffer = new byte[samples.length];
    for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += samples.length) {
      pagedFileStorage.get(offsetInFile, buffer, 0, buffer.length, false);
      assertArrayEquals(
        "getInt(" + offsetInFile + ")",
        samples,
        buffer
      );
    }
  }

  @Test
  public void putBytes_couldBeGetBack_afterReopen() throws IOException {
    byte[] samples = new byte[4096];
    ThreadLocalRandom.current().nextBytes(samples);

    for (long offsetInFile = 0; offsetInFile < 512 * samples.length; offsetInFile += samples.length) {
      pagedFileStorage.put(offsetInFile, samples, 0, samples.length);
    }

    pagedFileStorage.close();
    pagedFileStorage = open(path);

    long fileLength = pagedFileStorage.length();
    byte[] buffer = new byte[samples.length];
    for (long offsetInFile = 0; offsetInFile < fileLength; offsetInFile += samples.length) {
      pagedFileStorage.get(offsetInFile, buffer, 0, buffer.length, false);
      assertArrayEquals(
        "getInt(" + offsetInFile + ")",
        samples,
        buffer
      );
    }
  }


  //===================== infrastructure: ====================================================================================//

  private PagedFileStorage open(@NotNull Path path) {
    return new PagedFileStorage(path, storageLockContext, PAGE_SIZE, /*valuesAligned:*/ false, /*nativeByteOrder: */ false);
  }

  @Before
  public void setUp() throws IOException {
    path = tempDir.newFileNio("storage");
    withLock(storageLockContext, () -> {
      pagedFileStorage = open(path);
    });
  }

  @After
  public void tearDown() throws IOException {
    withLock(storageLockContext, pagedFileStorage::closeAndClean);
  }


  private static void withLock(@NotNull StorageLockContext lock,
                               @NotNull ThrowableRunnable<IOException> block) throws IOException {
    lock.lockWrite();
    try {
      block.run();
    }
    finally {
      lock.unlockWrite();
    }
  }
}