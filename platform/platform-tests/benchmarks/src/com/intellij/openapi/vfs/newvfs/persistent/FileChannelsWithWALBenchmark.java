// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.NioFiles;
import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLogFactory;
import com.intellij.platform.util.io.storages.circular.CircularBytesBuffer;
import com.intellij.platform.util.io.storages.circular.CircularBytesBufferOverMMappedFile;
import com.intellij.platform.util.io.storages.circular.CircularBytesBufferOverMMappedFileLocked;
import com.intellij.platform.util.io.storages.circular.WriteAheadLogOverCircularBuffer;
import com.intellij.platform.util.io.storages.enumerator.DurableEnumerator;
import com.intellij.platform.util.io.storages.enumerator.DurableEnumeratorFactory;
import com.intellij.util.io.OpenChannelsCache;
import com.intellij.util.io.ResilientFileChannel;
import com.intellij.util.io.writeaheadlog.FileChannelWithWAL;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;

import static com.intellij.util.io.IOUtil.MiB;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Measures positional {@link FileChannel} reads with and without concurrent positional writes.
 * <p>
 * The benchmark compares a plain channel, {@link FileChannelWithWAL} over the current circular buffer, and
 * {@link FileChannelWithWAL} over the old fully locked circular buffer baseline.
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Threads(4)
@Fork(1)
public class FileChannelsWithWALBenchmark {
  private static final AtomicInteger WAL_FLUSHER_THREAD_INDEX = new AtomicInteger();

  @State(Scope.Benchmark)
  public static class ReadOnlyContext extends ChannelContext {
  }

  @State(Scope.Benchmark)
  public static class ReadWriteContext extends ChannelContext {
    @Param("4")
    public int writerThreads;

    @Param("50")
    public int writerPauseUs;

    @Override
    protected int writerThreadCount() {
      return writerThreads;
    }

    @Override
    protected long writerPauseNanos() {
      return TimeUnit.MICROSECONDS.toNanos(writerPauseUs);
    }
  }

  @State(Scope.Benchmark)
  public abstract static class ChannelContext {
    @Param({"plain", "wal_locked", "wal_optimized"})
    public String implementation;

    @Param({"single", "striped", "random"})
    public String fileSelection;

    @Param({"1", "4", "16"})
    public int files;

    @Param("16")
    public int fileSizeMib;

    @Param("4096")
    public int recordSize;

    @Param("64")
    public int walCapacityMib;

    @Param("100")
    public int walFlushPeriodMs;

    @Param({"false", "true"})
    public boolean applyUnfinishedOnRead;

    private final AtomicInteger nextReaderIndex = new AtomicInteger();
    private final AtomicReference<Throwable> writerFailure = new AtomicReference<>();
    private final AtomicBoolean stopWriters = new AtomicBoolean();

    private Implementation parsedImplementation;
    private FileSelection parsedFileSelection;
    private Path tempRoot;
    private Path[] filePaths;
    private FileChannel[] channels;
    private OpenChannelsCache targetChannels;
    private WriteAheadLogOverCircularBuffer wal;
    private Thread[] writers;
    private WriterStatistics writerStatistics;
    private int recordsPerFile;
    private FileChannelWithWAL.FlushStatistics flushStatisticsAtStart;

    @Setup(Level.Iteration)
    public void setupIteration() throws IOException {
      parsedImplementation = Implementation.parse(implementation);
      parsedFileSelection = FileSelection.parse(fileSelection);
      validateParameters();

      nextReaderIndex.set(0);
      writerFailure.set(null);
      stopWriters.set(false);
      writerStatistics = new WriterStatistics();

      tempRoot = Files.createTempDirectory("FileChannelsWithWALBenchmark-");
      filePaths = createPrefilledFiles();
      channels = openBenchmarkChannels();
      flushStatisticsAtStart = FileChannelWithWAL.getFlushStatistics();
      startWriters();
    }

    @TearDown(Level.Iteration)
    public void tearDownIteration() throws IOException {
      stopWriters();

      ArrayList<Throwable> errors = new ArrayList<>();
      closeAll(errors);
      printIterationStatistics();
      deleteTempRoot(errors);

      if (!errors.isEmpty()) {
        IOException ioe = new IOException("Errors during benchmark teardown");
        errors.forEach(ioe::addSuppressed);
        throw ioe;
      }
    }

    public WorkerState newWorker() {
      return new WorkerState(nextReaderIndex.getAndIncrement(), ByteBuffer.allocateDirect(recordSize));
    }

    int randomRead(WorkerState worker) throws IOException {
      int fileIndex = selectReadFile(worker.workerIndex);
      long offset = randomRecordOffset();

      worker.readBuffer.clear();
      return channels[fileIndex].read(worker.readBuffer, offset);
    }

    void checkWriterFailure() throws IOException {
      Throwable failure = writerFailure.get();
      if (failure == null) {
        return;
      }
      if (failure instanceof IOException ioe) {
        throw ioe;
      }
      if (failure instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IOException("Background writer failed", failure);
    }

    protected int writerThreadCount() {
      return 0;
    }

    protected long writerPauseNanos() {
      return 0;
    }

    private void pauseWriterIfNeeded() {
      long pauseNanos = writerPauseNanos();
      if (pauseNanos > 0) {
        LockSupport.parkNanos(pauseNanos);
      }
    }

    private void validateParameters() {
      if (files <= 0) {
        throw new IllegalArgumentException("files must be positive: " + files);
      }
      if (fileSizeMib <= 0) {
        throw new IllegalArgumentException("fileSizeMib must be positive: " + fileSizeMib);
      }
      if (recordSize <= 0) {
        throw new IllegalArgumentException("recordSize must be positive: " + recordSize);
      }
      long fileSizeBytes = fileSizeBytes();
      if (fileSizeBytes < recordSize) {
        throw new IllegalArgumentException("fileSizeMib=" + fileSizeMib + " is too small for recordSize=" + recordSize);
      }
      recordsPerFile = Math.toIntExact(fileSizeBytes / recordSize);
      if (walCapacityMib <= 0) {
        throw new IllegalArgumentException("walCapacityMib must be positive: " + walCapacityMib);
      }
      if (writerThreadCount() < 0) {
        throw new IllegalArgumentException("writerThreads must not be negative: " + writerThreadCount());
      }
      if (writerPauseNanos() < 0) {
        throw new IllegalArgumentException("writerPauseNanos must not be negative: " + writerPauseNanos());
      }
      if (walFlushPeriodMs <= 0) {
        throw new IllegalArgumentException("walFlushPeriodMs must be positive: " + walFlushPeriodMs);
      }
    }

    private Path[] createPrefilledFiles() throws IOException {
      Path[] paths = new Path[files];
      for (int fileIndex = 0; fileIndex < files; fileIndex++) {
        Path file = tempRoot.resolve("target-" + fileIndex + ".dat");
        prefillFile(file, fileIndex);
        paths[fileIndex] = file;
      }
      return paths;
    }

    private void prefillFile(Path file, int fileIndex) throws IOException {
      int chunkSize = MiB;
      ByteBuffer chunk = ByteBuffer.allocateDirect(chunkSize);
      for (int i = 0; i < chunkSize; i++) {
        chunk.put((byte)(fileIndex + i));
      }
      chunk.flip();

      try (FileChannel channel = FileChannel.open(file, CREATE_NEW, READ, WRITE)) {
        long remaining = fileSizeBytes();
        long offset = 0;
        while (remaining > 0) {
          ByteBuffer source = chunk.duplicate();
          source.limit((int)Math.min(source.limit(), remaining));
          writeFully(channel, source, offset);
          int written = source.limit();
          offset += written;
          remaining -= written;
        }
        channel.force(false);
      }
    }

    @SuppressWarnings("resource")
    private FileChannel[] openBenchmarkChannels() throws IOException {
      FileChannel[] openedChannels = new FileChannel[files];
      switch (parsedImplementation) {
        case PLAIN -> {
          for (int i = 0; i < files; i++) {
            openedChannels[i] = FileChannel.open(filePaths[i], READ, WRITE);
          }
        }
        case WAL_LOCKED, WAL_OPTIMIZED -> {
          targetChannels = new OpenChannelsCache(
            "FileChannelsWithWALBenchmarkTargetFiles",
            files + 4,
            (path, readOnly) -> readOnly ? new ResilientFileChannel(path, READ) : new ResilientFileChannel(path, CREATE, READ, WRITE)
          );
          wal = openWal(parsedImplementation);
          for (int i = 0; i < files; i++) {
            //noinspection IOResourceOpenedButNotSafelyClosed
            openedChannels[i] = new FileChannelWithWAL(
              filePaths[i],
              wal,
              targetChannels.asWritable(),
              /* readOnly:              */ false,
              /* applyUnfinished:       */ applyUnfinishedOnRead,
              /* createFileImmediately: */ false
            );
          }
        }
      }
      return openedChannels;
    }

    private WriteAheadLogOverCircularBuffer openWal(Implementation implementationToOpen) throws IOException {
      Path walPath = tempRoot.resolve("write-ahead-log.wal");
      Path pathsEnumeratorPath = tempRoot.resolve("write-ahead-log.paths");
      CircularBytesBuffer circularBytesBuffer = switch (implementationToOpen) {
        case WAL_LOCKED -> CircularBytesBufferOverMMappedFileLocked.Factory
          .withCapacityAtLeast(walCapacityMib * MiB)
          .cleanIfFileIncompatible()
          .open(walPath);
        case WAL_OPTIMIZED -> CircularBytesBufferOverMMappedFile.Factory
          .withCapacityAtLeast(walCapacityMib * MiB)
          .cleanIfFileIncompatible()
          .open(walPath);
        case PLAIN -> throw new IllegalArgumentException("WAL is not applicable for " + implementationToOpen);
      };

      DurableEnumerator<Path> pathsEnumerator = DurableEnumeratorFactory
        .defaultWithInMemoryMap(WriteAheadLogOverCircularBuffer.getCANONICAL_PATH_DESCRIPTOR())
        .valuesLogFactory(
          AppendOnlyLogFactory.withDefaults()
            .pageSize(MiB)
            .cleanIfFileIncompatible()
            .failIfDataFormatVersionNotMatch(DurableEnumerator.DATA_FORMAT_VERSION)
        )
        .open(pathsEnumeratorPath);

      return new WriteAheadLogOverCircularBuffer(
        circularBytesBuffer,
        pathsEnumerator,
        this::writeToTargetFile,
        FileChannelsWithWALBenchmark::newWalFlusherThread,
        walFlushPeriodMs
      );
    }

    private void writeToTargetFile(@NotNull Path path,
                                   long offsetInFile,
                                   @NotNull ByteBuffer data) throws IOException {
      targetChannels.asWritable().executeOp(path, channel -> {
        writeFully(channel, data, offsetInFile);
        return null;
      });
    }

    private void startWriters() {
      int writerThreadCount = writerThreadCount();
      writers = new Thread[writerThreadCount];

      for (int writerIndex = 0; writerIndex < writerThreadCount; writerIndex++) {
        int index = writerIndex;
        Thread writer = new Thread(() -> runWriter(index), "FileChannelsWithWALBenchmark-writer-" + index);
        writer.setDaemon(true);
        writers[writerIndex] = writer;
        writer.start();
      }
    }

    private void runWriter(int writerIndex) {
      byte[] payload = new byte[recordSize];
      Arrays.fill(payload, (byte)(writerIndex + 1));
      ByteBuffer writeBuffer = ByteBuffer.wrap(payload);

      try {
        while (!stopWriters.get()) {
          int fileIndex = selectWriteFile(writerIndex);
          long offset = randomRecordOffset();

          writeBuffer.clear();

          long startedAtNs = System.nanoTime();
          writeFully(channels[fileIndex], writeBuffer, offset);
          writerStatistics.record(recordSize, System.nanoTime() - startedAtNs);
          pauseWriterIfNeeded();
        }
      }
      catch (Throwable t) {
        writerFailure.compareAndSet(null, t);
        stopWriters.set(true);
      }
    }

    private void stopWriters() {
      stopWriters.set(true);
      if (writers == null) {
        return;
      }

      boolean interrupted = false;
      for (Thread writer : writers) {
        if (writer == null) {
          continue;
        }
        while (writer.isAlive()) {
          try {
            writer.join();
          }
          catch (InterruptedException e) {
            interrupted = true;
          }
        }
      }
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }

    private int selectReadFile(int workerIndex) {
      return selectFile(workerIndex, false);
    }

    private int selectWriteFile(int writerIndex) {
      return selectFile(writerIndex, true);
    }

    private int selectFile(int workerIndex, boolean writer) {
      return switch (parsedFileSelection) {
        case SINGLE -> 0;
        case RANDOM -> ThreadLocalRandom.current().nextInt(files);
        case STRIPED -> selectStripedFile(workerIndex, writer);
      };
    }

    private int selectStripedFile(int workerIndex, boolean writer) {
      int writerThreadCount = writerThreadCount();
      if (writerThreadCount == 0 || files == 1) {
        return workerIndex % files;
      }

      int writerFileCount = Math.max(1, files / 2);
      if (writer) {
        return workerIndex % writerFileCount;
      }

      int readerFileCount = files - writerFileCount;
      return writerFileCount + workerIndex % readerFileCount;
    }

    private long randomRecordOffset() {
      return (long)ThreadLocalRandom.current().nextInt(recordsPerFile) * recordSize;
    }

    private long fileSizeBytes() {
      return (long)fileSizeMib * MiB;
    }

    private void closeAll(ArrayList<Throwable> errors) {
      closeChannels(errors);
      closeWal(errors);
      closeTargetChannels(errors);
    }

    private void closeChannels(ArrayList<Throwable> errors) {
      if (channels == null) {
        return;
      }

      for (FileChannel channel : channels) {
        if (channel == null) {
          continue;
        }
        try {
          channel.close();
        }
        catch (Throwable t) {
          errors.add(t);
        }
      }
    }

    private void closeWal(ArrayList<Throwable> errors) {
      if (wal == null) {
        return;
      }

      try {
        wal.close();
      }
      catch (Throwable t) {
        errors.add(t);
      }
    }

    private void closeTargetChannels(ArrayList<Throwable> errors) {
      if (targetChannels == null || filePaths == null) {
        return;
      }

      for (Path path : filePaths) {
        try {
          targetChannels.asWritable().closeChannel(path);
        }
        catch (Throwable t) {
          errors.add(t);
        }
      }
    }

    private void deleteTempRoot(ArrayList<Throwable> errors) {
      if (tempRoot == null) {
        return;
      }

      try {
        NioFiles.deleteRecursively(tempRoot);
      }
      catch (Throwable t) {
        errors.add(t);
      }
    }

    private void printIterationStatistics() {
      FileChannelWithWAL.FlushStatistics flushDelta = flushStatisticsDelta();
      System.out.println(
        "FileChannelsWithWALBenchmark stats: " +
        "implementation=" + implementation + ", fileSelection=" + fileSelection + ", files=" + files +
        ", fileSizeMib=" + fileSizeMib + ", recordSize=" + recordSize +
        ", walFlushPeriodMs=" + walFlushPeriodMs + ", applyUnfinishedOnRead=" + applyUnfinishedOnRead +
        ", writerThreads=" + writerThreadCount() + ", writerPauseNanos=" + writerPauseNanos() + ", writes=" + writerStatistics +
        ", flushDelta=" + flushDelta +
        (wal == null ? "" : ", wal=" + wal.getStatistics())
      );
    }

    private FileChannelWithWAL.FlushStatistics flushStatisticsDelta() {
      FileChannelWithWAL.FlushStatistics atStart = flushStatisticsAtStart;
      if (atStart == null) {
        return new FileChannelWithWAL.FlushStatistics(0, 0, 0, 0);
      }

      FileChannelWithWAL.FlushStatistics now = FileChannelWithWAL.getFlushStatistics();
      return new FileChannelWithWAL.FlushStatistics(
        now.getEntriesFlushedOnRead() - atStart.getEntriesFlushedOnRead(),
        now.getEntriesFlushedOnForce() - atStart.getEntriesFlushedOnForce(),
        now.getEntriesFlushedOnTruncate() - atStart.getEntriesFlushedOnTruncate(),
        now.getEntriesFlushedOnClose() - atStart.getEntriesFlushedOnClose()
      );
    }
  }

  @State(Scope.Thread)
  public static class ReadOnlyThreadState {
    WorkerState worker;

    @Setup(Level.Iteration)
    public void setupIteration(ReadOnlyContext context) {
      worker = context.newWorker();
    }
  }

  @State(Scope.Thread)
  public static class ReadWriteThreadState {
    WorkerState worker;

    @Setup(Level.Iteration)
    public void setupIteration(ReadWriteContext context) {
      worker = context.newWorker();
    }
  }



  @Benchmark
  public int randomReadsWithoutWrites(ReadOnlyContext context,
                                      ReadOnlyThreadState threadState) throws IOException {
    return context.randomRead(threadState.worker);
  }

  @Benchmark
  public int randomReadsWithWrites(ReadWriteContext context,
                                   ReadWriteThreadState threadState) throws IOException {
    context.checkWriterFailure();
    return context.randomRead(threadState.worker);
  }



  public static final class WorkerState {
    private final int workerIndex;
    private final ByteBuffer readBuffer;

    private WorkerState(int workerIndex, ByteBuffer readBuffer) {
      this.workerIndex = workerIndex;
      this.readBuffer = readBuffer;
    }
  }

  private enum Implementation {
    PLAIN,
    WAL_LOCKED,
    WAL_OPTIMIZED;

    static Implementation parse(String value) {
      return valueOf(value.toUpperCase(Locale.ROOT));
    }
  }

  private enum FileSelection {
    SINGLE,
    STRIPED,
    RANDOM;

    static FileSelection parse(String value) {
      return valueOf(value.toUpperCase(Locale.ROOT));
    }
  }

  private static final class WriterStatistics {
    private final LongAdder operations = new LongAdder();
    private final LongAdder bytes = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final AtomicLong maxNanos = new AtomicLong();

    void record(int bytesWritten, long elapsedNanos) {
      operations.increment();
      bytes.add(bytesWritten);
      totalNanos.add(elapsedNanos);
      updateMax(elapsedNanos);
    }

    @Override
    public String toString() {
      long ops = operations.sum();
      long nanos = totalNanos.sum();
      long averageNanos = ops == 0 ? 0 : nanos / ops;
      return "ops=" + ops + ", bytes=" + bytes.sum() + ", avgUs=" + averageNanos / 1_000.0 + ", maxUs=" + maxNanos.get() / 1_000.0;
    }

    private void updateMax(long elapsedNanos) {
      while (true) {
        long current = maxNanos.get();
        if (elapsedNanos <= current || maxNanos.compareAndSet(current, elapsedNanos)) {
          return;
        }
      }
    }
  }

  private static void writeFully(@NotNull FileChannel channel,
                                 @NotNull ByteBuffer source,
                                 long offset) throws IOException {
    long currentOffset = offset;
    while (source.hasRemaining()) {
      int written = channel.write(source, currentOffset);
      if (written <= 0) {
        Thread.yield();
      }
      else {
        currentOffset += written;
      }
    }
  }

  private static Thread newWalFlusherThread(@NotNull Runnable runnable) {
    Thread thread = new Thread(runnable, "FileChannelsWithWALBenchmark-wal-flusher-" + WAL_FLUSHER_THREAD_INDEX.getAndIncrement());
    thread.setDaemon(true);
    return thread;
  }


  static void main() throws RunnerException {
    Options opt = new OptionsBuilder()
      .jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED",
               "--add-opens=java.base/java.util=ALL-UNNAMED",
               "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
               "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",
               "-Xmx4g")
      .include(FileChannelsWithWALBenchmark.class.getSimpleName() + ".*")
      .threads(4)
      .forks(1)
      .build();

    new Runner(opt).run();
  }
}
