// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.immutableKeyValueStore.benchmark;

import com.intellij.util.lang.Murmur3_32Hash;
import org.openjdk.jmh.annotations.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/*

 Benchmark              Mode  Cnt     Score   Error  Units
 HashBenchmark.murmur3  avgt   10  2379.621 ± 9.704  ns/op
 HashBenchmark.xxh3     avgt   10   585.030 ± 0.691  ns/op

 */

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(iterations = 5, time = 4)
@Fork(2)
public class HashBenchmark {
  private static final byte[] data;

  static {
    data = new byte[5_000];
    new Random(42).nextBytes(data);
  }

  @Benchmark
  public int murmur3() {
    return Murmur3_32Hash.MURMUR3_32.hashBytes(data, 0, data.length);
  }
}
