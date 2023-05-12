// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import org.jetbrains.annotations.NotNull;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmark single attribute read (so far)
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
public class VFSAttributeAccessBenchmark {

  public static final FileAttribute TEST_ATTRIBUTE = new FileAttribute("TEST");

  @State(Scope.Benchmark)
  public static class Context {

    public File folder;

    public int folderId;
    private PersistentFSConnection connection;
    private PersistentFSAttributeAccessor attributeAccessor;

    @Setup
    public void setup(final ApplicationContext applicationContext) throws Exception {
      folder = FileUtil.createTempDirectory("VFSChildrenAccessBenchmark", "tst", /*deleteOnExit: */ true);

      final VirtualFile vFile = refreshAndFind(folder);
      folderId = ((VirtualFileWithId)vFile).getId();

      final Field implField = FSRecords.class.getDeclaredField("impl");
      final Field attributeAccessorField = FSRecordsImpl.class.getDeclaredField("attributeAccessor");
      final Field connectionField = FSRecordsImpl.class.getDeclaredField("connection");
      implField.setAccessible(true);
      connectionField.setAccessible(true);
      attributeAccessorField.setAccessible(true);

      final FSRecordsImpl impl = (FSRecordsImpl)implField.get(null);
      connection = (PersistentFSConnection)connectionField.get(impl);
      attributeAccessor = (PersistentFSAttributeAccessor)attributeAccessorField.get(impl);

      try (var stream = attributeAccessor.writeAttribute(folderId, TEST_ATTRIBUTE)) {
        stream.writeUTF("test string");
      }
    }

    @TearDown
    public void tearDown() throws Exception {
      if (folder != null) {
        FileUtilRt.deleteRecursively(folder.toPath());
      }
      //FIXME RC: EDT is not stopped, hence JMH can't terminate forked JVM.
    }

    @NotNull
    private static VirtualFile refreshAndFind(final @NotNull File file) {
      return Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file), file.getPath());
    }
  }

  @Benchmark
  public String readAttribute(final ApplicationContext application,
                              final Context context) throws IOException {
    try (var stream = context.attributeAccessor.readAttribute(context.folderId, TEST_ATTRIBUTE)) {
      return stream.readUTF();
    }
  }


  public static void main(final String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      .jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED",
               "--add-opens=java.base/java.util=ALL-UNNAMED",
               "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
               "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
               "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
               "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED"

               //to enable 'new' API:
               //"-Dvfs.lock-free-impl.enable=true",
               //"-Dvfs.lock-free-impl.fraction-direct-memory-to-utilize=0.5",
               //"-Dvfs.use-streamlined-attributes-storage=true"
      )
      //.mode(Mode.SingleShotTime)
      //.warmupIterations(1000)
      //.warmupBatchSize(1000)
      //.measurementIterations(1000)
      .include(VFSAttributeAccessBenchmark.class.getSimpleName() + ".*")
      .threads(1)
      .build();

    new Runner(opt).run();
  }
}
