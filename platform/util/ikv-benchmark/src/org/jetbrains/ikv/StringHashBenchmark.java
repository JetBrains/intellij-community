// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ikv;

import com.intellij.util.io.Murmur3_32Hash;
import net.openshift.hash.XxHash3;
import org.openjdk.jmh.annotations.*;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;


/*
Benchmark                    Mode  Cnt    Score   Error  Units
StringHashBenchmark.murmur3  avgt   10  293.428 ± 2.642  ns/op
StringHashBenchmark.xxh3     avgt   10  277.485 ± 9.273  ns/op
 */

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(iterations = 5, time = 4)
@Fork(2)
public class StringHashBenchmark {
  private static final String data;

  static {
    byte[] b = new byte[200];
    new Random(42).nextBytes(b);
    data = new BigInteger(b).toString(Character.MAX_RADIX);
  }

  @Benchmark
  public int murmur3() {
    return Murmur3_32Hash.MURMUR3_32.hashString(data, 0, data.length());
  }

  @Benchmark
  public int xxh3() {
    return XxHash3.hashUnencodedChars32(data);
  }
}
