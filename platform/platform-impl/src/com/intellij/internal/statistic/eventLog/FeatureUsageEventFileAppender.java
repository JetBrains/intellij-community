// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.helpers.CountingQuietWriter;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.spi.LoggingEvent;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.UUID;

public class FeatureUsageEventFileAppender extends FileAppender {
  protected long maxFileSize = 10 * 1024 * 1024;
  private long nextRollover = 0;

  private final Path myLogDirectory;

  public FeatureUsageEventFileAppender(@NotNull Layout layout, @NotNull Path dir, @NotNull String filename) throws IOException {
    super(layout, filename);
    myLogDirectory = dir;
  }

  public static FeatureUsageEventFileAppender create(@NotNull Layout layout, @NotNull Path dir) throws IOException {
    final File file = nextFile(dir);
    return new FeatureUsageEventFileAppender(layout, dir, file.getPath());
  }

  @NotNull
  public String getActiveLogName() {
    return StringUtil.isNotEmpty(fileName) ? PathUtil.getFileName(fileName) : "";
  }

  public void setMaxFileSize(String value) {
    maxFileSize = OptionConverter.toFileSize(value, maxFileSize + 1);
  }

  @NotNull
  protected CountingQuietWriter getQuietWriter() {
    return (CountingQuietWriter)this.qw;
  }

  protected void setQWForFiles(Writer writer) {
    this.qw = new CountingQuietWriter(writer, errorHandler);
  }

  public synchronized void setFile(String fileName, boolean append, boolean bufferedIO, int bufferSize) throws IOException {
    super.setFile(fileName, append, bufferedIO, bufferSize);
    if (append && qw instanceof CountingQuietWriter) {
      File f = new File(fileName);
      getQuietWriter().setCount(f.length());
    }
  }

  protected void subAppend(LoggingEvent event) {
    super.subAppend(event);
    if (fileName != null && qw != null) {
      long size = getQuietWriter().getCount();
      if (size >= maxFileSize && size >= nextRollover) {
        rollOver();
      }
    }
  }

  public void rollOver() {
    nextRollover = getQuietWriter().getCount() + maxFileSize;
    try {
      final File file = nextFile(myLogDirectory);
      setFile(file.getPath(), false, bufferedIO, bufferSize);
      nextRollover = 0;
    }
    catch (InterruptedIOException e) {
      Thread.currentThread().interrupt();
    }
    catch (Exception e) {
      LogLog.error("setFile(" + fileName + ", false) call failed.", e);
    }
  }

  @NotNull
  private static File nextFile(@NotNull Path dir) {
    File file = dir.resolve(UUID.randomUUID() + ".log").toFile();
    while (file.exists()) {
      file = dir.resolve(UUID.randomUUID() + ".log").toFile();
    }
    return file;
  }
}
