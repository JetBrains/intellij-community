// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.util.indexing.IndexingStamp;
import com.intellij.util.indexing.IndexingStampStorageOverRegularAttributes;
import com.intellij.util.indexing.TimestampsImmutable;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks different approaches to access index timestamps (file attribute).
 * New API allows raw access to the segment page ByteBuffer, while old API allows only access through InputStream
 * over copied byte[] array.
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class IndexStampAccessBenchmark {

  @State(Scope.Benchmark)
  public static class Context {
    public File file;

    public int fileId;

    @Setup
    public void setup(final ApplicationContext application) throws Exception {
      file = FileUtil.createTempFile("IndexStampAccessBenchmark", "tst", /*deleteOnExit: */ true);

      final VirtualFile vFile = refreshAndFind(file);
      fileId = ((VirtualFileWithId)vFile).getId();

      IndexingStamp.update(fileId, IdIndex.NAME, System.currentTimeMillis());
      IndexingStamp.flushCaches();
    }

    @TearDown
    public void tearDown() throws Exception {
      if (file != null) {
        file.delete();
      }
    }

    @NotNull
    private static VirtualFile refreshAndFind(final @NotNull File file) {
      return Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file), file.getPath());
    }
  }

  @Benchmark
  public TimestampsImmutable readIndexStamps_ViaRawByteBuffer(final ApplicationContext application,
                                                     final Context context) {
    return FSRecords.readAttributeRawWithLock(
      context.fileId,
      IndexingStampStorageOverRegularAttributes.PERSISTENCE,
      TimestampsImmutable::readTimestamps
    );
  }

  @Benchmark
  public TimestampsImmutable readIndexStamps_ViaInputStream(final ApplicationContext application,
                                                   final Context context) throws IOException {
    try (final DataInputStream stream = FSRecords.readAttributeWithLock(context.fileId, IndexingStampStorageOverRegularAttributes.PERSISTENCE)) {
      return TimestampsImmutable.readTimestamps(stream);
    }
  }

  //FIXME RC: EDT is not stopped, hence JMH can't terminate forked JVM.
  //          Move it to FSRecordsContext instead of ApplicationContext


  public static void main(final String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED",
               "--add-opens=java.base/java.util=ALL-UNNAMED",
               "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
               "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
               "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
               "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",

               //to enable 'new' API:
               "-Dvfs.lock-free-impl.enable=true",
               "-Dvfs.lock-free-impl.fraction-direct-memory-to-utilize=0.5",
               "-Dvfs.use-streamlined-attributes-storage=true"
      )
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      .include(IndexStampAccessBenchmark.class.getSimpleName() + ".*")
      .threads(1)
      .build();

    new Runner(opt).run();
  }
}
