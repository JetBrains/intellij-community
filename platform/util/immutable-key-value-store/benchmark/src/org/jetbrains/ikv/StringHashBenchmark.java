// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ikv;

import com.intellij.util.lang.Murmur3_32Hash;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.util.concurrent.TimeUnit;

/*
Benchmark                              Mode  Cnt   Score   Error  Units
StringHashBenchmark.murmur3            avgt   25  59.986 ± 4.639  ns/op
StringHashBenchmark.murmur3_unencoded  avgt   25  40.002 ± 0.369  ns/op
StringHashBenchmark.xxh3            avgt    5  29,033 ± 5,166  ns/op
StringHashBenchmark.xxh3_unencoded  avgt    5  62,121 ± 5,109  ns/op
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class StringHashBenchmark {
  private static final String data = "com/intellij/profiler/async/windows/WinAsyncProfilerLocator";

  @Benchmark
  public int murmur3() {
    return Murmur3_32Hash.MURMUR3_32.hashString(data, 0, data.length());
  }

  @Benchmark
  public int murmur3_unencoded() {
    return Murmur3_32Hash.MURMUR3_32.hashUnencodedChars(data);
  }
}
