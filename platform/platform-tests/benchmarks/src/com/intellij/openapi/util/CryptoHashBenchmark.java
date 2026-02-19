// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSContentAccessor;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
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

import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Benchmark for MessageDigest used in VFS content storage
 */
@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(NANOSECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = SECONDS)
@Fork(1)
@Threads(1)
public class CryptoHashBenchmark {

  @State(Scope.Benchmark)
  public static class DataContext {

    @Param({"64", "1024", "16384"})
    private int contentSize;

    public byte[] content;

    @Setup
    public void setup() throws Exception {
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      content = new byte[contentSize];
      rnd.nextBytes(content);
    }

    @TearDown
    public void tearDown() throws Exception {
    }
  }

  @Benchmark
  public byte[] calculateHash(DataContext context) {
    byte[] content = context.content;
    return PersistentFSContentAccessor.calculateHash(content, 0, content.length);
  }

  public static void main(String[] args) throws RunnerException {
    final Options opt = new OptionsBuilder()
      //.mode(Mode.SampleTime)
      .include(CryptoHashBenchmark.class.getSimpleName() + ".*")
      .threads(1)
      .build();

    new Runner(opt).run();
  }
}
