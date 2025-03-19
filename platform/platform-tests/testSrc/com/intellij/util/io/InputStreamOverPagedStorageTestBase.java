// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;

public abstract class InputStreamOverPagedStorageTestBase {

  protected static final int PAGE_SIZE = 1024;

  protected static final int BYTES_SIZE_TO_TEST = 16 << 20;

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void bytesWrittenIntoStorage_ReadBackAsIs_OneByOne() throws Exception {
    byte[] bytesWritten = writeRandomBytesToStorage(BYTES_SIZE_TO_TEST);

    try (InputStream stream = streamOverStorage(0, bytesWritten.length)) {
      for (int i = 0; i < bytesWritten.length; i++) {
        final byte writtenByte = bytesWritten[i];
        final int readByte = stream.read();
        assertNotEquals(
          "Must not return EoF marker",
          -1,
          readByte
        );
        assertEquals(
          "written[" + i + "] != read[" + i + "]",
          writtenByte,
          (byte)readByte
        );
      }
    }
  }

  @Test
  public void bytesWrittenIntoStorage_ReadBackAsIs_AsArray() throws Exception {
    byte[] bytesWritten = writeRandomBytesToStorage(BYTES_SIZE_TO_TEST);

    try (InputStream stream = streamOverStorage(0, bytesWritten.length)) {
      byte[] bytesReadBack = new byte[bytesWritten.length];
      int bytesActuallyRead = stream.read(bytesReadBack);
      assertEquals(
        "Bytes count actually read must be same as were written",
        bytesWritten.length,
        bytesActuallyRead
      );
      assertArrayEquals(
        "Bytes actually read must be same as were written",
        bytesWritten,
        bytesReadBack
      );
    }
  }

  @Test
  public void failToCreateStreamLargerThanMaxInteger() {
    assertThrows(
      IllegalArgumentException.class,
      () -> streamOverStorage(0, (long)Integer.MAX_VALUE + 1)
    );
  }

  @Test
  public void failToCreateStreamLargerThanStorage() throws IOException {
    byte[] bytesWritten = writeRandomBytesToStorage(BYTES_SIZE_TO_TEST);
    assertThrows(
      IllegalArgumentException.class,
      () -> streamOverStorage(0, bytesWritten.length + 1)
    );
  }

  @Test
  public void zeroLengthRead_AlwaysSucceed() throws IOException {
    byte[] bytesWritten = writeRandomBytesToStorage(0);
    try(var stream = streamOverStorage(0, 0)){
      byte[] bytesReadBack = new byte[bytesWritten.length];
      int bytesActuallyRead = stream.read(bytesReadBack, 0, 0);
      assertEquals(
        "read(length: 0) should always return 0",
        0,
        bytesActuallyRead
      );
    }
  }

  @Test
  public void readsBeyondEOF_ShouldReturnMinusOne() throws IOException {
    writeRandomBytesToStorage(BYTES_SIZE_TO_TEST);
    try(var stream = streamOverStorage(0, 0)){
      byte[] bytesReadBack = new byte[10];
      int bytesActuallyRead = stream.read(bytesReadBack);
      assertEquals(
        "read(position >= EOF) should return -1",
        -1,
        bytesActuallyRead
      );
    }
  }

  @Test
  public void readsWithLengthMoreThanRemains_ShouldReturnRemains() throws IOException {
    writeRandomBytesToStorage(BYTES_SIZE_TO_TEST);
    int remains = 10;
    try(var stream = streamOverStorage(0, remains)){
      byte[] bytesReadBack = new byte[20];
      int bytesActuallyRead = stream.read(bytesReadBack);
      assertEquals(
        "read(length > remains) should return (remains)",
        remains,
        bytesActuallyRead
      );
    }
  }

  //================== infrastructure: ====================================================


  protected abstract @NotNull InputStream streamOverStorage(long position, long limit);

  protected abstract byte[] writeRandomBytesToStorage(int bytesCount) throws IOException;

  protected static byte[] randomBytes(int size) {
    byte[] byteWritten = new byte[size];
    ThreadLocalRandom.current().nextBytes(byteWritten);
    return byteWritten;
  }
}
