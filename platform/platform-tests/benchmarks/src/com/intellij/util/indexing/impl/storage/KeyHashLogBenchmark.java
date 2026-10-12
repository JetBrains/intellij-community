// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.indexing.IdFilter;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import it.unimi.dsi.fastutil.ints.IntSet;
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

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/// Measures the full scan and the common lookup after one append to a large key-hash log
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
@Fork(value = 1, jvmArgsAppend = {
  "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
  "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",
  "-Didea.index.cashed.hashids=true",
  "-Xmx4g"
})
@Threads(1)
public class KeyHashLogBenchmark {
  private static final int UNIQUE_KEY_COUNT = 100_000;

  private static final IdFilter ALL_FILES = new IdFilter() {
    @Override
    public boolean containsFileId(int id) {
      return true;
    }

    @Override
    public @NotNull FilterScopeType getFilteringScopeType() {
      return FilterScopeType.PROJECT_AND_LIBRARIES;
    }
  };

  // KeyHashLog uses project identity, hashCode, and the project root tracker service for this cache path
  private static final Project PROJECT = (Project)Proxy.newProxyInstance(
    Project.class.getClassLoader(),
    new Class<?>[]{Project.class},
    (ignoredProxy, method, ignoredArgs) -> {
      if (method.getName().equals("hashCode")) return 1;
      if (method.getName().equals("getService")) return null;
      throw new UnsupportedOperationException(method.toString());
    }
  );

  @State(Scope.Benchmark)
  public static class LogState {
    // Matches the record count of the reported KotlinFunctionShortNameIndex.project log
    @Param("7000000")
    public int recordsCount;

    private Path tempDir;

    protected KeyHashLog<Integer> keyHashLog;
    protected int nextInputId;

    @Setup(Level.Trial)
    public void fillUpKeyHashLog() throws Exception {
      tempDir = Files.createTempDirectory("key-hash-log-benchmark");
      keyHashLog = new KeyHashLog<>(
        EnumeratorIntegerDescriptor.INSTANCE,
        tempDir.resolve("keys")
      );
      for (int inputId = 1; inputId <= recordsCount; inputId++) {
        int key = inputId % UNIQUE_KEY_COUNT;
        keyHashLog.addKeyHashToVirtualFileMapping(key, inputId);
      }
      nextInputId = recordsCount + 1;
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
      keyHashLog.close();
      NioFiles.deleteRecursively(tempDir);
    }
  }

  //A state there
  // - .getSuitableKeyHashes() has been called beforehand
  // - keyHashLog is _modified_ before each benchmark method execution
  @State(Scope.Benchmark)
  public static class TailScanState extends LogState {

    @Setup(Level.Trial)
    public void warmGetSuitableKeyHashes() throws Exception {
      keyHashLog.getSuitableKeyHashes(ALL_FILES, PROJECT);
    }

    @Setup(Level.Invocation)
    public void appendOneRecord() throws Exception {
      keyHashLog.addKeyHashToVirtualFileMapping(nextInputId % UNIQUE_KEY_COUNT, nextInputId++);
    }
  }

  @Benchmark
  public IntSet fullScan(LogState state) throws Exception {
    return state.keyHashLog.getSuitableKeyHashes(ALL_FILES);
  }

  @Benchmark
  public IntPredicate lookupAfterAppend(TailScanState state) throws Exception {
    return state.keyHashLog.getSuitableKeyHashes(ALL_FILES, PROJECT);
  }
}
