// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
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
 * Benchmarks different approaches to access CHILDREN (file attribute).
 * New API allows raw access to the segment page ByteBuffer, while old API allows only access through InputStream
 * over copied byte[] array.
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
public class VFSChildrenAccessBenchmark {

  @State(Scope.Benchmark)
  public static class Context {

    @Param({"2", "8", "64"})
    public int CHILDREN_COUNT = 16;


    public File folder;

    public int folderId;

    public PersistentFSTreeAccessor oldTreeAccessor;
    public PersistentFSTreeRawAccessor newTreeAccessor;

    @Setup
    public void setup(final ApplicationContext applicationContext) throws Exception {
      folder = FileUtil.createTempDirectory("VFSChildrenAccessBenchmark", "tst", /*deleteOnExit: */ true);
      CHILDREN_COUNT = 16;
      for (int i = 0; i < CHILDREN_COUNT; i++) {
        FileUtil.createTempFile(folder, "1", ".tst", true);
      }


      final VirtualFile vFile = refreshAndFind(folder);
      folderId = ((VirtualFileWithId)vFile).getId();

      final Field implField = FSRecords.class.getDeclaredField("impl");
      final Field attributeAccessorField = FSRecordsImpl.class.getDeclaredField("attributeAccessor");
      final Field connectionField = FSRecordsImpl.class.getDeclaredField("connection");
      implField.setAccessible(true);
      connectionField.setAccessible(true);
      attributeAccessorField.setAccessible(true);

      final FSRecordsImpl impl = (FSRecordsImpl)implField.get(null);
      final PersistentFSConnection connection = (PersistentFSConnection)connectionField.get(impl);
      final PersistentFSAttributeAccessor attributeAccessor = (PersistentFSAttributeAccessor)attributeAccessorField.get(impl);
      oldTreeAccessor = new PersistentFSTreeAccessor(attributeAccessor, connection);
      newTreeAccessor = new PersistentFSTreeRawAccessor(attributeAccessor, connection);
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
  public int[] listChildrenIds_old(final ApplicationContext application,
                                   final Context context) throws IOException {
    return context.oldTreeAccessor.listIds(context.folderId);
  }

  @Benchmark
  public int[] listChildrenIds_new(final ApplicationContext application,
                                   final Context context) throws IOException {
    return context.newTreeAccessor.listIds(context.folderId);
  }


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
      .include(VFSChildrenAccessBenchmark.class.getSimpleName() + ".*")
      .threads(1)
      .build();

    new Runner(opt).run();
  }
}
