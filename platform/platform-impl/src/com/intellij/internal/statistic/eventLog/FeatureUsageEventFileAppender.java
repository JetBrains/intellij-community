// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.helpers.CountingQuietWriter;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.spi.LoggingEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class FeatureUsageEventFileAppender extends FileAppender {
  protected long maxFileAge = 14 * 24 * 60 * 60 * 1000;
  protected long maxFileSize = 10 * 1024 * 1024;

  private long nextRollover = 0;
  protected long oldestExistingFile = -1;

  private final Path myLogDirectory;
  private final Supplier<List<File>> myFilesProducer;

  @TestOnly
  public FeatureUsageEventFileAppender(@NotNull Path path, @NotNull List<File> files) {
    myLogDirectory = path;
    myFilesProducer = () -> files;
  }

  public FeatureUsageEventFileAppender(@NotNull Layout layout, @NotNull Path dir, @NotNull String filename) throws IOException {
    super(layout, filename);
    myLogDirectory = dir;
    myFilesProducer = () -> {
      final File[] files = dir.toFile().listFiles();
      return files == null || files.length == 0 ? ContainerUtil.emptyList() : ContainerUtil.newArrayList(files);
    };
    cleanUpOldFiles();
  }

  public static FeatureUsageEventFileAppender create(@NotNull Layout layout, @NotNull Path dir) throws IOException {
    final File file = nextFile(dir);
    return new FeatureUsageEventFileAppender(layout, dir, file.getPath());
  }

  @NotNull
  public String getActiveLogName() {
    return StringUtil.isNotEmpty(fileName) ? PathUtil.getFileName(fileName) : "";
  }

  public void setMaxFileAge(long maxAge) {
    maxFileAge = maxAge;
  }

  public void setMaxFileSize(String value) {
    maxFileSize = OptionConverter.toFileSize(value, maxFileSize + 1);
  }

  @NotNull
  protected CountingQuietWriter getQuietWriter() {
    return (CountingQuietWriter)this.qw;
  }

  @Override
  protected void setQWForFiles(Writer writer) {
    this.qw = new CountingQuietWriter(writer, errorHandler);
  }

  @Override
  public synchronized void setFile(String fileName, boolean append, boolean bufferedIO, int bufferSize) throws IOException {
    super.setFile(fileName, append, bufferedIO, bufferSize);
    if (append && qw instanceof CountingQuietWriter) {
      File f = new File(fileName);
      getQuietWriter().setCount(f.length());
    }
  }

  @Override
  protected void subAppend(LoggingEvent event) {
    super.subAppend(event);
    if (fileName != null && qw != null) {
      long size = getQuietWriter().getCount();
      if (size >= maxFileSize && size >= nextRollover) {
        rollOver();
        cleanUpOldFiles();
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

  protected void cleanUpOldFiles() {
    long oldestAcceptable = System.currentTimeMillis() - maxFileAge;
    if (oldestExistingFile != -1 && oldestAcceptable < oldestExistingFile) {
      return;
    }

    cleanUpOldFiles(oldestAcceptable);
  }

  protected void cleanUpOldFiles(long oldestAcceptable) {
    final List<File> logs = myFilesProducer.get();
    if (logs == null || logs.isEmpty()) {
      return;
    }

    final String activeLog = getActiveLogName();
    long oldestFile = -1;
    for (File file : logs) {
      if (StringUtil.equals(file.getName(), activeLog)) continue;

      final long lastModified = file.lastModified();
      if (lastModified < oldestAcceptable) {
        if (!file.delete()) {
          LogLog.error("Failed deleting old file " + file);
        }
      }
      else if (lastModified < oldestFile || oldestFile == -1) {
        oldestFile = lastModified;
      }
    }
    oldestExistingFile = oldestFile;
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
