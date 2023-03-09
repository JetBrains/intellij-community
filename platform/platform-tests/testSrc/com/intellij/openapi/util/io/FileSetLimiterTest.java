// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.*;

public class FileSetLimiterTest {

  public static final int MAX_FILES_TO_KEEP = 14;

  @Rule
  public final TemporaryFolder temporaryDirectory = new TemporaryFolder();

  @Test
  public void createdFileNameHasDesiredFormat() throws IOException, ParseException {
    final Path dir = temporaryDirectory.newFolder().toPath();

    final String dateTimeFormat = "yyyy-MM-dd-HH-mm-ss";
    final String dateAsString = "2022-11-04-21-08-13";

    final Date parsedDateTime = new SimpleDateFormat(dateTimeFormat)
      .parse(dateAsString);
    final Clock clock = clockPositionedAt(parsedDateTime.getTime());

    final Path path = FileSetLimiter.inDirectory(dir)
      .withBaseNameAndDateFormatSuffix("my-file.log", dateTimeFormat)
      .withMaxFilesToKeep(MAX_FILES_TO_KEEP)
      .createNewFile(clock);

    assertEquals("Created file should have name [my-file." + dateAsString + ".log]",
                 path.getFileName().toString(),
                 "my-file." + dateAsString + ".log"
    );
  }

  @Test
  public void limiterKeepsLimitTheTotalNumberOfExistingFiles() throws IOException {
    final Path dir = temporaryDirectory.newFolder().toPath();
    final long nowMs = System.currentTimeMillis();

    for (int filesCreated = 1; filesCreated <= 2 * MAX_FILES_TO_KEEP; filesCreated++) {
      final Clock clock = clockPositionedAt(nowMs + SECONDS.toMillis(filesCreated));

      FileSetLimiter.inDirectory(dir)
        .withBaseNameAndDateFormatSuffix("my-file.log", "yyyy-MM-dd-HH-mm-ss")
        .withMaxFilesToKeep(MAX_FILES_TO_KEEP)
        .createNewFile(clock);

      try (var files = Files.list(dir)) {
        final long filesCount = files.count();
        assertEquals(
          "No more than maxFilesToKeep(" + MAX_FILES_TO_KEEP + ") files are exist in any given moment",
          Math.min(filesCreated, MAX_FILES_TO_KEEP),
          filesCount
        );
      }
    }
  }

  @Test
  public void limiterRemovesOldestFilesAndKeepNewestOnes() throws IOException {
    final Path dir = temporaryDirectory.newFolder().toPath();
    final long nowMs = System.currentTimeMillis();

    final List<Path> createdFiles = new ArrayList<>();
    for (int filesCreated = 1; filesCreated <= 4 * MAX_FILES_TO_KEEP; filesCreated++) {
      final Clock clock = clockPositionedAt(nowMs + SECONDS.toMillis(filesCreated));

      final Path newFile = FileSetLimiter.inDirectory(dir)
        .withBaseNameAndDateFormatSuffix("my-file.log", "yyyy-MM-dd-HH-mm-ss")
        .withMaxFilesToKeep(MAX_FILES_TO_KEEP)
        .createNewFile(clock);
      createdFiles.add(newFile);

      for (int i = 0; i < createdFiles.size(); i++) {
        final Path createdFile = createdFiles.get(i);
        final int indexCountingBackwards = createdFiles.size() - i;
        if (indexCountingBackwards <= MAX_FILES_TO_KEEP) {
          assertTrue(
            "File " + createdFile + " is fresh enough (" + indexCountingBackwards + "-th), should still exist",
            Files.exists(createdFile)
          );
        }
        else {
          assertFalse(
            "File " + createdFile + " is old enough (" + indexCountingBackwards + "-th) should NOT exist",
            Files.exists(createdFile)
          );
        }
      }
    }
  }

  @NotNull
  private static Clock clockPositionedAt(final long nowMs) {
    return Clock.fixed(
      Instant.ofEpochMilli(nowMs),
      ZoneId.systemDefault()
    );
  }
}