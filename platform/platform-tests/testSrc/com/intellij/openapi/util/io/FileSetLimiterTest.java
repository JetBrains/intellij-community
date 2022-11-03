// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

/**
 *
 */
public class FileSetLimiterTest {
  private static final MessageFormat DEFAULT_FILENAME_FORMAT = new MessageFormat("my-file.{0, date, yyyy-MM-dd-HH-mm-ss}.csv");

  @Rule
  public final TemporaryFolder temporaryDirectory = new TemporaryFolder();

  @Test
  public void limiterKeepsLimitOnTheNumberOfExistingFiles() throws IOException {
    final Path dir = temporaryDirectory.newFolder().toPath();
    final int maxFilesToKeep = 14;
    final long nowMs = System.currentTimeMillis();

    for (int i = 1; i <= 2 * maxFilesToKeep; i++) {
      final Clock clock = clockAt(nowMs + SECONDS.toMillis(i));

      final Path path = FileSetLimiter.inDirectory(dir)
        .withBaseNameAndDateFormatSuffix("my-file.log", "yyyy-MM-dd-HH-mm-ss")
        .withMaxFilesToKeep(maxFilesToKeep)
        .createNewFile(clock);

      try (var files = Files.list(dir)) {
        final long filesCount = files.count();
        assertEquals(
          "No more than maxFilesToKeep(" + maxFilesToKeep + ") files are exist in any given moment",
          Math.min(i, maxFilesToKeep),
          filesCount
        );
      }
    }
  }

  @NotNull
  private static Clock clockAt(final long nowMs) {
    final long nextMs = nowMs;
    final Clock clock = Clock.fixed(
      Instant.ofEpochMilli(nextMs),
      ZoneId.systemDefault()
    );
    return clock;
  }
}