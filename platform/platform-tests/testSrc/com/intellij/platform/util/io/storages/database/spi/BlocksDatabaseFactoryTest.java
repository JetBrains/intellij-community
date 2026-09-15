// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.spi;

import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the public lifecycle of the first database.meta implementation. */
public class BlocksDatabaseFactoryTest {
  private static final int CHUNK_SIZE = 1024 * 1024;

  @Test
  public void fsyncOptionsBelongToTheFactoryInstance() {
    var defaults = BlocksDatabaseFactory.withDefaults();
    assertEquals(MMappedFileStorage.FSYNC_ON_FLUSH_BY_DEFAULT, defaults.fsyncOnFlush());
    assertFalse(defaults.fsyncOnClose(), "The default close must avoid fsync");

    var factory = defaults.fsyncOnFlush(false).fsyncOnClose(true).chunkSize(CHUNK_SIZE);
    assertFalse(factory.fsyncOnFlush(), "Changing another option must preserve fsyncOnFlush");
    assertTrue(factory.fsyncOnClose(), "Changing another option must preserve fsyncOnClose");
  }

  /** Reopening must read and validate the Database header from the first log record. */
  @Test
  public void databaseHeaderSurvivesReopening(@TempDir Path tempDirectory) throws Exception {
    var factory = factory(CHUNK_SIZE);
    try (var database = factory.open(tempDirectory)) {
      assertFalse(database.isClosed(), "A newly created database must accept operations");
      assertFalse(database.isDirty(), "Creating the Database header must force its persistent identity");
    }

    try (var database = factory.open(tempDirectory)) {
      assertFalse(database.isClosed(), "A reopened database must accept operations");
    }
  }

  /** A conflicting chunk size must not silently reinterpret existing chunk files. */
  @Test
  public void reopeningRejectsDifferentChunkSize(@TempDir Path tempDirectory) throws Exception {
    try (var database = factory(CHUNK_SIZE).open(tempDirectory)) {
      assertFalse(database.isClosed(), "The setup database must open before compatibility is tested");
    }

    var exception = assertThrows(
      IOException.class,
      () -> {
        try (var database = factory(CHUNK_SIZE * 2).open(tempDirectory)) {
          assertFalse(database.isClosed(), "A database with an incompatible chunk size must never become available");
        }
      },
      "Reopening with a different chunk size must fail"
    );
    assertTrue(exception.getMessage().contains("chunkSize"), "The failure must identify the incompatible format field");
  }

  /** The public factory must own database.meta without exposing its log implementation. */
  @Test
  public void publicFactoryCreatesAndClosesDatabaseCatalog(@TempDir Path tempDirectory) throws Exception {
    var database = factory(CHUNK_SIZE).open(tempDirectory);
    assertTrue(Files.exists(tempDirectory.resolve("database.meta")), "Opening the database must create database.meta");

    database.flush();
    database.close();
    assertTrue(database.isClosed(), "Closing the database must release database.meta");
  }

  /** Creates the small storage configuration used by metadata tests. */
  private static BlocksDatabaseFactory factory(int chunkSize) {
    return BlocksDatabaseFactory
      .withDefaults()
      .chunkSize(chunkSize);
  }
}
